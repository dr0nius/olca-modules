package org.openlca.core.library.csv;

import org.apache.commons.csv.CSVRecord;
import org.openlca.core.library.Proto;
import org.openlca.util.Strings;

import java.util.List;

/**
 * Contains the meta-data of a location stored in a library index.
 */
public record LibLocation(String id, String name, String code) {

	private static final LibLocation empty = new LibLocation(
		null, null, null);

	static LibLocation empty() {
		return empty;
	}

	boolean isEmpty() {
		return id == null || id.isBlank();
	}

	Proto.Location toProto() {
		return Proto.Location.newBuilder()
			.setId(Strings.orEmpty(id))
			.setName(Strings.orEmpty(name))
			.setCode(Strings.orEmpty(code))
			.build();
	}

	static LibLocation fromProto(Proto.Location proto) {
		return new LibLocation(
			proto.getId(),
			proto.getName(),
			proto.getCode());
	}

	void toCsv(List<String> buffer) {
		buffer.add(Csv.str(id));
		buffer.add(Csv.str(name));
		buffer.add(Csv.str(code));
	}

	static LibLocation fromCsv(CSVRecord row, int offset) {
		return new LibLocation(
			Csv.read(row, offset),
			Csv.read(row, offset + 1),
			Csv.read(row, offset + 2));
	}
}
