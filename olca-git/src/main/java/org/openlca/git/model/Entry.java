package org.openlca.git.model;

import org.eclipse.jgit.lib.ObjectId;
import org.openlca.core.model.ModelType;
import org.openlca.git.util.GitUtil;
import org.openlca.util.Strings;

public class Entry extends Reference {

	public final String name;
	public final EntryType typeOfEntry;

	public Entry(String path, String commitId, String name, ObjectId objectId) {
		super(getModelType(path, name), getRefId(name), commitId, getFullPath(path, name), objectId);
		this.name = this.refId == null ? GitUtil.decode(name) : name;
		if (Strings.nullOrEmpty(path)) {
			typeOfEntry = EntryType.MODEL_TYPE;
		} else if (this.refId == null) {
			typeOfEntry = EntryType.CATEGORY;
		} else {
			typeOfEntry = EntryType.DATASET;
		}
	}

	private static ModelType getModelType(String path, String name) {
		if (Strings.nullOrEmpty(path))
			return ModelType.valueOf(name);
		if (!path.contains("/"))
			return ModelType.valueOf(path);
		return ModelType.valueOf(path.substring(0, path.indexOf("/")));
	}

	private static String getRefId(String name) {
		if (!name.contains(".json"))
			return null;
		var refId = name.substring(0, name.indexOf("."));
		if (!GitUtil.isUUID(refId))
			return null;
		return refId;
	}

	private static String getFullPath(String path, String name) {
		if (Strings.nullOrEmpty(path))
			return name;
		return path + "/" + name;
	}

	public static enum EntryType {

		MODEL_TYPE,
		CATEGORY,
		DATASET;

	}
}