package org.openlca.git.writer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.Executors;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.file.PackInserter;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.openlca.git.GitConfig;
import org.openlca.git.iterator.ChangeIterator;
import org.openlca.git.model.Change;
import org.openlca.git.model.DiffType;
import org.openlca.git.util.GitUtil;
import org.openlca.git.util.ProgressMonitor;
import org.openlca.git.util.Repositories;
import org.openlca.jsonld.SchemaVersion;
import org.openlca.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO check error handling
public class CommitWriter {

	private static final Logger log = LoggerFactory.getLogger(CommitWriter.class);
	private final GitConfig config;
	private final PersonIdent committer;
	private final ProgressMonitor progressMonitor;
	private PackInserter packInserter;
	private ObjectInserter objectInserter;
	private Converter converter;
	private boolean isMergeCommit;
	private boolean isStashCommit;

	public CommitWriter(GitConfig config, PersonIdent committer) {
		this(config, committer, null);
	}

	public CommitWriter(GitConfig config, PersonIdent committer, ProgressMonitor progressMonitor) {
		this.config = config;
		this.committer = committer;
		this.progressMonitor = progressMonitor;
	}

	public String commit(String message, List<Change> changes) throws IOException {
		isMergeCommit = false;
		isStashCommit = false;
		return _commit(message, changes, null, null);
	}

	public String stashCommit(String message, List<Change> changes) throws IOException {
		isMergeCommit = false;
		isStashCommit = true;
		return _commit(message, changes, null, null);
	}

	public String mergeCommit(String message, List<Change> changes, String localCommitId, String remoteCommitId)
			throws IOException {
		isMergeCommit = true;
		isStashCommit = false;
		return _commit(message, changes, localCommitId, remoteCommitId);
	}

	public String _commit(String message, List<Change> changes, String localCommitId, String remoteCommitId)
			throws IOException {
		var threads = Executors.newCachedThreadPool();
		try {
			var previousCommit = Repositories.headCommitOf(config.repo);
			if (previousCommit != null && !isCurrentSchemaVersion())
				throw new IOException("Git repo is not in current schema version");
			if (changes.isEmpty() && (previousCommit == null || localCommitId == null || remoteCommitId == null))
				return null;
			if (progressMonitor != null) {
				progressMonitor.beginTask("Writing commit", changes.size());
			}
			packInserter = config.repo.getObjectDatabase().newPackInserter();
			packInserter.checkExisting(config.checkExisting);
			objectInserter = config.repo.newObjectInserter();
			converter = new Converter(config, threads);
			converter.start(changes.stream()
					.filter(d -> d.diffType != DiffType.DELETED)
					.sorted((d1, d2) -> Strings.compare(d1.path, d2.path))
					.toList());
			var localCommitOid = localCommitId != null
					? ObjectId.fromString(localCommitId)
					: previousCommit != null
							? previousCommit.getId()
							: ObjectId.zeroId();
			var remoteCommitOid = remoteCommitId != null
					? ObjectId.fromString(remoteCommitId)
					: ObjectId.zeroId();
			var localTreeId = getCommitTreeId(localCommitOid);
			var remoteTreeId = getCommitTreeId(remoteCommitOid);
			var treeId = syncTree("", new ChangeIterator(config, changes), localTreeId, remoteTreeId);
			if (!isStashCommit && config.store != null) {
				config.store.save();
			}
			var commitId = commit(message, treeId, localCommitOid, remoteCommitOid);
			return commitId.name();
		} finally {
			if (packInserter != null) {
				packInserter.flush();
				packInserter.close();
				packInserter = null;
			}
			if (objectInserter != null) {
				objectInserter.flush();
				objectInserter.close();
				objectInserter = null;
			}
			if (converter != null) {
				converter.clear();
				converter = null;
			}
			threads.shutdown();
		}
	}

	private ObjectId getCommitTreeId(ObjectId commitId) throws IOException {
		if (commitId == null || commitId.equals(ObjectId.zeroId()))
			return null;
		var commit = config.repo.parseCommit(commitId);
		if (commit == null)
			return null;
		return commit.getTree().getId();
	}

	private ObjectId syncTree(String prefix, ChangeIterator diffIterator, ObjectId localTreeId,
			ObjectId remoteTreeId) {
		boolean appended = false;
		var tree = new TreeFormatter();
		try (var walk = createWalk(prefix, diffIterator, localTreeId, remoteTreeId)) {
			while (walk.next()) {
				var mode = walk.getFileMode();
				var name = walk.getNameString();
				ObjectId id = null;
				if (mode == FileMode.TREE) {
					id = handleTree(walk, diffIterator);
				} else if (mode == FileMode.REGULAR_FILE) {
					id = handleFile(walk);
				}
				if (id == null || id.equals(ObjectId.zeroId()))
					continue;
				tree.append(name, mode, id);
				appended = true;
			}
		} catch (Exception e) {
			log.error("Error walking tree", e);
		}
		if (!appended && !Strings.nullOrEmpty(prefix)) {
			if (!isStashCommit && config.store != null) {
				config.store.remove(prefix);
			}
			return null;
		}
		if (Strings.nullOrEmpty(prefix) && localTreeId == null && remoteTreeId == null) {
			appendSchemaVersion(tree);
		}
		try {
			var newId = objectInserter.insert(tree);
			if (!isStashCommit && config.store != null) {
				config.store.put(prefix, newId);
			}
			return newId;
		} catch (IOException e) {
			log.error("Error inserting tree", e);
			return null;
		}
	}

	private TreeWalk createWalk(String prefix, ChangeIterator diffIterator, ObjectId localTreeId,
			ObjectId remoteTreeId) throws IOException {
		var walk = new TreeWalk(config.repo);
		addTree(walk, prefix, localTreeId);
		addTree(walk, prefix, remoteTreeId);
		if (diffIterator != null) {
			walk.addTree(diffIterator);
		} else {
			walk.addTree(new EmptyTreeIterator());
		}
		return walk;
	}

	private void addTree(TreeWalk walk, String prefix, ObjectId treeId)
			throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException, IOException {
		if (treeId == null || treeId.equals(ObjectId.zeroId())) {
			walk.addTree(new EmptyTreeIterator());
		} else if (Strings.nullOrEmpty(prefix)) {
			walk.addTree(treeId);
		} else {
			walk.addTree(new CanonicalTreeParser(GitUtil.encode(prefix).getBytes(), walk.getObjectReader(), treeId));
		}
	}

	private ObjectId handleTree(TreeWalk walk, ChangeIterator diffIterator) {
		var localTreeId = walk.getFileMode(0) != FileMode.MISSING ? walk.getObjectId(0) : null;
		var remoteTreeId = walk.getFileMode(1) != FileMode.MISSING ? walk.getObjectId(1) : null;
		var iterator = walk.getFileMode(2) != FileMode.MISSING ? diffIterator.createSubtreeIterator() : null;
		if (iterator == null && localTreeId == null)
			return remoteTreeId;
		if (iterator == null && remoteTreeId == null)
			return localTreeId;
		var prefix = GitUtil.decode(walk.getPathString());
		return syncTree(prefix, iterator, localTreeId, remoteTreeId);
	}

	private ObjectId handleFile(TreeWalk walk)
			throws IOException, InterruptedException {
		var localBlobId = walk.getFileMode(0) != FileMode.MISSING ? walk.getObjectId(0) : null;
		var remoteBlobId = walk.getFileMode(1) != FileMode.MISSING ? walk.getObjectId(1) : null;
		if (walk.getFileMode(2) == FileMode.MISSING)
			return isMergeCommit && remoteBlobId != null ? remoteBlobId : localBlobId;
		var path = GitUtil.decode(walk.getPathString());
		var iterator = walk.getTree(2, ChangeIterator.class);
		Change change = iterator.getEntryData();
		var file = iterator.getEntryFile();
		if (change.diffType == DiffType.DELETED && matches(path, change, file)) {
			if (file == null && !isStashCommit && config.store != null) {
				config.store.remove(path);
			}
			return null;
		}
		if (file != null)
			return packInserter.insert(Constants.OBJ_BLOB, Files.readAllBytes(file.toPath()));
		if (progressMonitor != null) {
			progressMonitor.subTask("Writing", change);
		}
		var data = converter.take(path);
		localBlobId = packInserter.insert(Constants.OBJ_BLOB, data);
		if (!isStashCommit && config.store != null) {
			config.store.put(path, localBlobId);
		}
		if (progressMonitor != null) {
			progressMonitor.worked(1);
		}
		return localBlobId;
	}

	private void appendSchemaVersion(TreeFormatter tree) {
		try {
			var schemaBytes = SchemaVersion.current().toJson().toString().getBytes(StandardCharsets.UTF_8);
			var blobId = packInserter.insert(Constants.OBJ_BLOB, schemaBytes);
			if (blobId != null) {
				tree.append(SchemaVersion.FILE_NAME, FileMode.REGULAR_FILE, blobId);
			}
		} catch (Exception e) {
			log.error("Error inserting schema version", e);
		}
	}

	private boolean matches(String path, Change change, File file) {
		if (change == null)
			return false;
		if (file == null)
			return path.equals(change.path);
		return path.startsWith(change.path.substring(0, change.path.lastIndexOf(GitUtil.DATASET_SUFFIX)));
	}

	private ObjectId commit(String message, ObjectId treeId, ObjectId localCommitId, ObjectId remoteCommitId) {
		try {
			var commit = new CommitBuilder();
			commit.setAuthor(committer);
			commit.setCommitter(committer);
			commit.setMessage(message);
			commit.setEncoding(StandardCharsets.UTF_8);
			commit.setTreeId(treeId);
			if (localCommitId != null && !localCommitId.equals(ObjectId.zeroId())) {
				commit.addParentId(localCommitId);
			}
			if (remoteCommitId != null && !remoteCommitId.equals(ObjectId.zeroId())) {
				commit.addParentId(remoteCommitId);
			}
			var commitId = objectInserter.insert(commit);
			if (isStashCommit) {
				updateRef(Constants.R_STASH, message, commitId);
			} else {
				updateRef(Constants.HEAD, message, commitId);
			}
			return commitId;
		} catch (IOException e) {
			log.error("failed to update head", e);
			return null;
		}
	}

	private void updateRef(String ref, String message, ObjectId commitId) throws IOException {
		var update = config.repo.updateRef(ref);
		update.setNewObjectId(commitId);
		if (!isStashCommit) {
			update.update();
		} else {
			update.setRefLogIdent(committer);
			update.setRefLogMessage(message, false);
			update.setForceRefLog(true);
			update.forceUpdate();
		}
	}

	private boolean isCurrentSchemaVersion() {
		var schema = Repositories.versionOf(config.repo);
		return schema.isCurrent();
	}

}