package org.openlca.git.writer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;

import org.eclipse.jgit.internal.storage.file.PackInserter;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.openlca.git.Config;
import org.openlca.git.iterator.DiffIterator;
import org.openlca.git.model.Diff;
import org.openlca.git.model.DiffType;
import org.openlca.git.util.ObjectIds;
import org.openlca.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO check error handling
public class CommitWriter {

	private static final Logger log = LoggerFactory.getLogger(CommitWriter.class);
	private final Config config;
	private PackInserter inserter;
	private Converter converter;

	public CommitWriter(Config config) {
		this.config = config;
	}

	public String commit(String message, List<Diff> diffs) throws IOException {
		if (diffs.isEmpty())
			return null;
		var threads = Executors.newCachedThreadPool();
		try {
			inserter = config.repo.getObjectDatabase().newPackInserter();
			inserter.checkExisting(config.checkExisting);
			converter = new Converter(config, threads);
			converter.start(diffs.stream()
					.filter(d -> d.type != DiffType.DELETED)
					.toList());
			var previousCommitTreeId = getPreviousCommitTreeId();
			var treeId = syncTree("", previousCommitTreeId, new DiffIterator(diffs));
			config.store.save();
			var commitId = commit(treeId, message);
			return commitId.name();
		} finally {
			if (inserter != null) {
				inserter.flush();
				inserter.close();
			}
			converter.clear();
			threads.shutdown();
		}
	}

	private ObjectId syncTree(String prefix, ObjectId treeId, DiffIterator diffIterator) {
		boolean appended = false;
		var tree = new TreeFormatter();
		try (var walk = createWalk(prefix, treeId, diffIterator)) {
			while (walk.next()) {
				var mode = walk.getFileMode();
				var name = walk.getNameString();
				ObjectId id = null;
				if (mode == FileMode.TREE) {
					id = handleTree(walk, diffIterator);
				} else if (mode == FileMode.REGULAR_FILE) {
					id = handleFile(walk);
				}
				if (ObjectIds.isNullOrZero(id))
					continue;
				tree.append(name, mode, id);
				appended = true;
			}
		} catch (Exception e) {
			log.error("Error walking tree " + treeId, e);
		}
		if (!appended && !Strings.nullOrEmpty(prefix)) {
			config.store.invalidate(prefix);
			return null;
		}
		var newId = insert(i -> i.insert(tree));
		config.store.put(prefix, newId);
		return newId;
	}

	private TreeWalk createWalk(String prefix, ObjectId treeId, DiffIterator diffIterator) throws IOException {
		var walk = new TreeWalk(config.repo);
		if (treeId == null || treeId.equals(ObjectId.zeroId())) {
			walk.addTree(new EmptyTreeIterator());
		} else if (Strings.nullOrEmpty(prefix)) {
			walk.addTree(treeId);
		} else {
			walk.addTree(new CanonicalTreeParser(prefix.getBytes(), walk.getObjectReader(), treeId));
		}
		walk.addTree(diffIterator);
		return walk;
	}

	private ObjectId handleTree(TreeWalk walk, DiffIterator diffIterator) {
		var treeId = walk.getObjectId(0);
		if (walk.getFileMode(1) == FileMode.MISSING)
			return treeId;
		var prefix = walk.getPathString();
		return syncTree(prefix, treeId, diffIterator.createSubtreeIterator());
	}

	private ObjectId handleFile(TreeWalk walk)
			throws IOException, InterruptedException {
		var blobId = walk.getObjectId(0);
		if (walk.getFileMode(1) == FileMode.MISSING)
			return blobId;
		var path = walk.getPathString();
		Diff diff = walk.getTree(1, DiffIterator.class).getEntryData();
		boolean matchesPath = diff != null && diff.path().equals(path);
		if (matchesPath && diff.type == DiffType.DELETED) {
			config.store.invalidate(path);
			return null;
		}
		var data = converter.take(path);
		if (diff.type == DiffType.MODIFIED && ObjectIds.equal(data, blobId))
			return blobId;
		blobId = inserter.insert(Constants.OBJ_BLOB, data);
		config.store.put(path, blobId);
		return blobId;
	}

	private ObjectId getPreviousCommitTreeId() {
		try (var walk = new RevWalk(config.repo)) {
			var head = config.repo.resolve("refs/heads/master");
			if (head == null)
				return null;
			var commit = walk.parseCommit(head);
			if (commit == null)
				return null;
			return commit.getTree().getId();
		} catch (IOException e) {
			log.error("Error reading commit tree", e);
			return null;
		}
	}

	private ObjectId commit(ObjectId treeId, String message) {
		try {
			var commit = new CommitBuilder();
			commit.setAuthor(config.committer);
			commit.setCommitter(config.committer);
			commit.setMessage(message);
			commit.setEncoding(StandardCharsets.UTF_8);
			commit.setTreeId(treeId);
			var head = config.repo.findRef("HEAD");
			var previousCommitId = head.getObjectId();
			if (previousCommitId != null) {
				commit.addParentId(previousCommitId);
			}
			var commitId = insert(i -> i.insert(commit));
			var update = config.repo.updateRef(head.getName());
			update.setNewObjectId(commitId);
			update.update();
			return commitId;
		} catch (IOException e) {
			log.error("failed to update head", e);
			return null;
		}
	}

	private ObjectId insert(Insert insertion) {
		try (var inserter = config.repo.newObjectInserter()) {
			return insertion.insertInto(inserter);
		} catch (IOException e) {
			log.error("failed to insert", e);
			return null;
		}
	}

	private interface Insert {

		ObjectId insertInto(ObjectInserter inserter) throws IOException;

	}

}