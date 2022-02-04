package org.openlca.jsonld.io;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openlca.core.Tests;
import org.openlca.core.database.IDatabase;
import org.openlca.core.model.Flow;
import org.openlca.core.model.FlowProperty;
import org.openlca.core.model.ImpactCategory;
import org.openlca.core.model.Location;
import org.openlca.core.model.Parameter;
import org.openlca.core.model.ParameterRedef;
import org.openlca.core.model.ParameterRedefSet;
import org.openlca.core.model.Process;
import org.openlca.core.model.ProductSystem;
import org.openlca.core.model.UnitGroup;
import org.openlca.jsonld.MemStore;
import org.openlca.jsonld.input.JsonImport;
import org.openlca.jsonld.output.JsonExport;

import java.util.Objects;
import java.util.function.Consumer;

public class JsonNewFieldsV2Test {

	private final IDatabase db = Tests.getDb();
	private Process process;

	@Before
	public void setup() {
		var units = UnitGroup.of("Mass units", "kg");
		var mass = FlowProperty.of("Mass", units);
		var product = Flow.product("product", mass);
		process = Process.of("process", product);
		db.insert(units, mass, product, process);
	}

	@After
	public void cleanup() {
		db.clear();
	}

	@Test
	public void testLibraryAndTags() {
		process.library = "some-lib";
		process.tags="some,tags";
		db.update(process);
		var store = withExport(export -> export.write(process));
		db.delete(process);
		new JsonImport(store, db).run();
		var copy = db.get(Process.class, process.refId);
		assertEquals("some-lib", copy.library);
		assertEquals("some,tags", copy.tags);
	}

	private MemStore withExport(Consumer<JsonExport> fn) {
		var store = new MemStore();
		var export = new JsonExport(db, store);
		fn.accept(export);
		return store;
	}

	@Test
	public void testFlowLocations() {
		var mass = process.quantitativeReference.flow.referenceFlowProperty;
		var flow = db.insert(Flow.elementary("F", mass));
		var location = db.insert(Location.of("Loc"));

		// for exchanges
		var exchange = process.output(flow, 2);
		exchange.location = location;
		process = db.update(process);

		// for impact factors
		var impact = ImpactCategory.of("Ind");
		impact.factor(flow, 10).location = location;
		db.insert(impact);

		var store = withExport(export -> {
			export.write(process);
			export.write(impact);
		});
		db.clear();
		new JsonImport(store, db).run();

		var processCopy = db.get(Process.class, process.refId);
		var exchangeCopy = processCopy.exchanges.stream()
			.filter(e -> Objects.equals(flow.refId, e.flow.refId))
			.findFirst()
			.orElseThrow();
		assertEquals("Loc", exchangeCopy.location.name);

		var impactCopy = db.get(ImpactCategory.class, impact.refId);
		var factorCopy = impactCopy.impactFactors.stream()
			.filter(f -> Objects.equals(flow.refId, f.flow.refId))
			.findFirst()
			.orElseThrow();
		assertEquals("Loc", factorCopy.location.name);
	}

	@Test
	public void testParameterRedefSets() {

		// create a product system with parameter sets
		var param = db.insert(Parameter.global("global", 42));
		var system = ProductSystem.of("system", process);

		var redef1 = ParameterRedef.of(param, 42);
		var set1 = ParameterRedefSet.of("baseline", redef1);
		set1.isBaseline = true;
		system.parameterSets.add(set1);

		var redef2 = ParameterRedef.of(param, 21);
		var set2 = ParameterRedefSet.of("alternative", redef2);
		system.parameterSets.add(set2);
		db.insert(system);

		// export and import the system
		var store = new MemStore();
		var export = new JsonExport(db, store);
		export.write(system);
		db.clear();
		assertNull(db.get(ProductSystem.class, system.refId));
		new JsonImport(store, db).run();

		// check
		var copy = db.get(ProductSystem.class, system.refId);
		assertNotNull(copy);

		var s1 = copy.parameterSets.stream()
			.filter(s -> "baseline".equals(s.name))
			.findFirst()
			.orElseThrow();
		assertTrue(s1.isBaseline);
		assertEquals(1, s1.parameters.size());
		var rd1 = s1.parameters.get(0);
		assertEquals("global", rd1.name);
		assertEquals(42, rd1.value, 1e-10);

		var s2 = copy.parameterSets.stream()
			.filter(s -> "alternative".equals(s.name))
			.findFirst()
			.orElseThrow();
		assertFalse(s2.isBaseline);
		assertEquals(1, s2.parameters.size());
		var rd2 = s2.parameters.get(0);
		assertEquals("global", rd2.name);
		assertEquals(21, rd2.value, 1e-10);
	}
}
