package org.openlca.git.find;

import java.io.IOException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.openlca.core.model.ModelType;

class ModelFilter extends TreeFilter {

	private final ModelType type;
	private final String refId;

	ModelFilter(ModelType type, String refId) {
		this.type = type;
		this.refId = refId;
	}

	@Override
	public boolean include(TreeWalk tw)
			throws MissingObjectException, IncorrectObjectTypeException, IOException {
		if (tw.getFileMode() == FileMode.TREE)
			return tw.getPathString().startsWith(type.name());
		var name = tw.getNameString();
		return name.equals(refId + ".proto") || name.equals(refId + ".json");
	}

	@Override
	public boolean shouldBeRecursive() {
		return true;
	}

	@Override
	public TreeFilter clone() {
		return new ModelFilter(type, refId);
	}

}