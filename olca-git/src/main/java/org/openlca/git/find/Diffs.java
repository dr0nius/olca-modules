package org.openlca.git.find;

import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.openlca.core.model.ModelType;
import org.openlca.git.model.Diff;
import org.openlca.git.util.GitUtil;
import org.openlca.jsonld.SchemaVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Diffs {

	static final Logger log = LoggerFactory.getLogger(References.class);
	private final FileRepository repo;

	public static Diffs of(FileRepository repo) {
		return new Diffs(repo);
	}

	private Diffs(FileRepository repo) {
		this.repo = repo;
	}

	public Find find() {
		return new Find();
	}

	public class Find {

		private String leftCommitId;
		private String rightCommitId;
		private String path;

		public Find withPrevious(String commitId) {
			var commits = Commits.of(repo);
			// TODO this is wrong for merge commits
			var left = commitId != null ? commits.find().before(commitId).latest() : null;
			leftCommitId = left != null ? left.id : null;
			rightCommitId = commitId;
			return this;
		}

		public Find between(String leftId, String rightId) {
			this.leftCommitId = leftId;
			this.rightCommitId = rightId;
			return this;
		}

		public Find path(String path) {
			this.path = GitUtil.encode(path);
			return this;
		}

		public Find type(ModelType type) {
			this.path = type != null ? type.name() : null;
			return this;
		}

		public List<Diff> all() {
			try (var walk = new TreeWalk(repo)) {
				var commits = Commits.of(repo);
				var leftRev = leftCommitId != null ? commits.getRev(leftCommitId) : null;
				var rightRev = rightCommitId != null ? commits.getRev(rightCommitId) : null;
				addCommitTree(walk, leftRev);
				addCommitTree(walk, rightRev);
				walk.setRecursive(true);
				var filter = AndTreeFilter.create(
						NotBinaryFilter.create(),
						PathFilter.create(SchemaVersion.FILE_NAME).negate());
				if (path != null) {
					filter = AndTreeFilter.create(filter, PathFilter.create(path));
				}
				walk.setFilter(filter);
				return DiffEntry.scan(walk).stream()
						.map(d -> new Diff(d, leftCommitId, rightCommitId))
						.toList();
			} catch (IOException e) {
				log.error("Error getting diff", e);
				return null;
			}
		}

		private void addCommitTree(TreeWalk walk, RevCommit commit) throws IOException {
			if (commit == null) {
				walk.addTree(new EmptyTreeIterator());
				return;
			}
			walk.addTree(commit.getTree());
		}

	}

}
