package org.openlca.git;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.openlca.jsonld.Json;
import org.openlca.jsonld.JsonStoreReader;
import org.openlca.jsonld.JsonStoreWriter;
import org.openlca.jsonld.LibraryLink;
import org.openlca.jsonld.PackageInfo;
import org.openlca.jsonld.SchemaVersion;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public record RepositoryInfo(JsonObject json) {
	
	public static final String FILE_NAME = PackageInfo.FILE_NAME;
	static final int REPOSITORY_CLIENT_VERSION_FALLBACK = 1;
	public static final int REPOSITORY_CURRENT_CLIENT_VERSION = 2;
	public static final List<Integer> REPOSITORY_SUPPORTED_CLIENT_VERSIONS = Arrays.asList(1, 2);
	static final int REPOSITORY_SERVER_VERSION_FALLBACK = 1;
	public static final int REPOSITORY_CURRENT_SERVER_VERSION = 2;
	public static final List<Integer> REPOSITORY_SUPPORTED_SERVER_VERSIONS = Arrays.asList(1, 2);
	
	public static RepositoryInfo of(JsonElement json) {
		var obj = json != null && json.isJsonObject()
				? json.getAsJsonObject()
				: new JsonObject();
		return new RepositoryInfo(obj);
	}

	public static RepositoryInfo create() {
		var json = PackageInfo.create().json();
		Json.put(json, "repositoryClientVersion", REPOSITORY_CURRENT_CLIENT_VERSION);
		Json.put(json, "repositoryServerVersion", REPOSITORY_CURRENT_SERVER_VERSION);
		return new RepositoryInfo(json);
	}

	public static RepositoryInfo readFrom(JsonStoreReader reader) {
		var elem = reader.getJson(FILE_NAME);
		return of(elem);
	}

	public void writeTo(JsonStoreWriter writer) {
		writer.put(FILE_NAME, json);
	}

	public SchemaVersion schemaVersion() {
		return PackageInfo.of(json).schemaVersion();
	}

	public List<LibraryLink> libraries() {
		return PackageInfo.of(json).libraries();
	}

	public int repositoryClientVersion() {
		return Json.getInt(json, "repositoryClientVersion", REPOSITORY_CLIENT_VERSION_FALLBACK);
	}

	public int repositoryServerVersion() {
		return Json.getInt(json, "repositoryServerVersion", REPOSITORY_SERVER_VERSION_FALLBACK);
	}

	public RepositoryInfo withLibraries(Collection<LibraryLink> links) {
		var json = PackageInfo.of(this.json).withLibraries(links).json();
		return of(json);
	}

	public RepositoryInfo withSchemaVersion(SchemaVersion version) {
		var json = PackageInfo.of(this.json).withSchemaVersion(version).json();
		return of(json);
	}

	public RepositoryInfo withRepositoryClientVersion(int version) {
		Json.put(json, "repositoryClientVersion", version);
		return this;
	}

	public RepositoryInfo withRepositoryServerVersion(int version) {
		Json.put(json, "repositoryServerVersion", version);
		return this;
	}

}
