package org.openlca.git.find;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.openlca.core.model.ModelType;
import org.openlca.git.model.Reference;
import org.openlca.git.util.GitUtil;
import org.openlca.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class References {

	static final Logger log = LoggerFactory.getLogger(References.class);
	private final FileRepository repo;

	public References(FileRepository repo) {
		this.repo = repo;
	}

	public Reference get(ModelType type, String refId, String commitId) {
		// TODO check if all() or changed()
		// TODO efficiency
		var refs = find().model(type, refId).commit(commitId).all();
		if (refs.isEmpty())
			return null;
		return refs.get(0);
	}

	public Find find() {
		return new Find();
	}

	private Reference createRef(String prefix, TreeWalk walk, String commitId, int tree) {
		return new Reference(getType(prefix, walk), getRefId(walk), commitId, walk.getPathString(),
				walk.getObjectId(tree));
	}

	private ModelType getType(String prefix, TreeWalk walk) {
		var path = prefix;
		if (Strings.nullOrEmpty(path)) {
			path = walk.getPathString();
		}
		if (path.contains("/")) {
			path = path.substring(0, path.indexOf("/"));
		}
		return ModelType.valueOf(path);
	}

	private String getRefId(TreeWalk walk) {
		var name = walk.getNameString();
		return name.substring(0, name.indexOf("."));
	}

	public class Find {

		private String path;
		private String commitId;
		private String changedSince;
		private ModelType type;
		private String refId;

		public Find path(String path) {
			this.path = GitUtil.encode(path);
			return this;
		}

		public Find commit(String commitId) {
			this.commitId = commitId;
			return this;
		}

		public Find changedSince(String commitId) {
			this.changedSince = commitId;
			return this;
		}

		public Find type(ModelType type) {
			this.path = type != null ? type.name() : null;
			return this;
		}

		public Find model(ModelType type, String refId) {
			this.type = type;
			this.refId = refId;
			return this;
		}

		public long count() {
			return get(true).size();
		}

		public List<Reference> all() {
			return get(false);
		}

		private List<Reference> get(boolean countOnly) {
			try {
				var commits = new Commits(repo);
				var commit = commits.getRev(commitId);
				if (commit == null)
					return new ArrayList<>();
				var commitId = commit.getId().name();
				try (var walk = new TreeWalk(repo)) {
					var refs = new ArrayList<Reference>();
					var onlyChanged = changedSince != null;
					if (changedSince != null) {
						var previous = commits.find().from(changedSince).before(commitId).latest();
						if (previous != null) {
							walk.addTree(commits.getRev(previous.id).getTree());
						} else {
							onlyChanged = false;
						}
					}
					walk.addTree(commit.getTree());
					walk.setRecursive(true);
					TreeFilter filter = null;
					if (path != null) {
						filter = addFilter(filter, PathFilter.create(path));
					}
					if (type != null && refId != null) {
						filter = addFilter(filter, new ModelFilter(type, refId));
					}
					if (onlyChanged) {
						filter = addFilter(filter, TreeFilter.ANY_DIFF);
					}
					if (filter != null) {
						walk.setFilter(filter);
					}
					// TODO filter binaries
					while (walk.next()) {
						if (countOnly) {
							refs.add(null);
						} else {
							refs.add(createRef(null, walk, commitId, onlyChanged ? 1 : 0));
						}
					}
					return refs;
				}
			} catch (IOException e) {
				log.error("Error getting references, type: " + type + ", refId: " + refId + ", commit: " + commitId
						+ ", path: " + path, e);
				return new ArrayList<>();
			}
		}

		private TreeFilter addFilter(TreeFilter current, TreeFilter newFilter) {
			return current != null ? AndTreeFilter.create(current, newFilter) : newFilter;
		}

	}

}
