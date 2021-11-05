package org.openlca.io.simapro.csv;

import com.google.common.base.Strings;
import org.openlca.core.model.Category;
import org.openlca.simapro.csv.enums.ElementaryFlowType;
import org.openlca.simapro.csv.enums.SubCompartment;

record Compartment(ElementaryFlowType type, SubCompartment sub) {

	static Compartment of(ElementaryFlowType type, SubCompartment sub) {
		return new Compartment(type, sub);
	}

	static Compartment of(String path) {
		if (path == null)
			return null;
		String[] parts = path.split("/");
		int n = parts.length;
		if (n == 1) {
			Compartment c = find(parts[0], null);
			if (c != null)
				return c;
		} else if (n > 1) {
			String sub = parts[n - 1];
			String comp = parts[n - 2];
			Compartment c = find(comp, sub);
			if (c != null)
				return c;
		}
		return matchPath(path);
	}

	static Compartment of(Category category) {
		if (category == null || category.name == null)
			return null;

		// try to directly identify the compartment
		// from the last two category names
		if (category.category == null) {
			Compartment c = find(category.name, null);
			if (c != null)
				return c;
		} else {
			String comp = category.category.name;
			String sub = category.name;
			if (comp != null) {
				Compartment c = find(comp, sub);
				if (c != null)
					return c;
			}
		}

		// try to identify it from identifiers in the
		// full category path
		StringBuilder p = null;
		Category c = category;
		while (c != null) {
			String name = c.name == null ? "" : c.name;
			if (p == null) {
				p = new StringBuilder(name);
			} else {
				p.insert(0, name + '/');
			}
			c = c.category;
		}
		String path = p.toString().toLowerCase();
		return matchPath(path);
	}

	private static Compartment matchPath(String path) {
		// find compartments for the path
		// try to match more specific first

		// social flows
		if (match(path, "social")) {
			return Compartment.of(
				ElementaryFlowType.SOCIAL_ISSUES, SubCompartment.UNSPECIFIED);
		}

		// resources
		if (match(path, "resource")) {
			var type = ElementaryFlowType.RESOURCES;

			if (match(path, "bio"))
				return Compartment.of(
					type, SubCompartment.RESOURCES_BIOTIC);

			if (match(path, "water"))
				return Compartment.of(
					type, SubCompartment.RESOURCES_IN_WATER);

			if (match(path, "air"))
				return Compartment.of(
					type, SubCompartment.RESOURCES_IN_AIR);

			if (match(path, "land"))
				return Compartment.of(
					type, SubCompartment.RESOURCES_LAND);

			if (match(path, "ground"))
				return Compartment.of(
					type, SubCompartment.RESOURCES_IN_GROUND);

			return Compartment.of(type, SubCompartment.UNSPECIFIED);
		} // resources


		// air emissions
		if (match(path, "emission", "air")) {
			var type = ElementaryFlowType.EMISSIONS_TO_AIR;

			if (match(path, "stratosphere", "troposhere"))
				return Compartment.of(
					type, SubCompartment.AIR_STRATOSPHERE_TROPOSHERE);

			if (match(path, "stratosphere"))
				return Compartment.of(
					type, SubCompartment.AIR_STRATOSPHERE);

			if (match(path, "low", "pop", "long", "term"))
				return Compartment.of(
					type, SubCompartment.AIR_LOW_POP_LONG_TERM);

			if (match(path, "low", "pop"))
				return Compartment.of(
					type, SubCompartment.AIR_LOW_POP);

			if (match(path, "high", "pop"))
				return Compartment.of(
					type, SubCompartment.AIR_HIGH_POP);

			if (match(path, "indoor"))
				return Compartment.of(
					type, SubCompartment.AIR_INDOOR);

			return Compartment.of(type, SubCompartment.UNSPECIFIED);
		} // air emissions


		// soil emissions
		if (match(path, "emission", "soil")) {
			var type = ElementaryFlowType.EMISSIONS_TO_SOIL;

			if (match(path, "non-agri"))
				return Compartment.of(
					type, SubCompartment.UNSPECIFIED);

			if (match(path, "agri"))
				return Compartment.of(
					type, SubCompartment.SOIL_AGRICULTURAL);

			if (match(path, "forest"))
				return Compartment.of(
					type, SubCompartment.SOIL_FORESTRY);

			if (match(path, "urban"))
				return Compartment.of(
					type, SubCompartment.SOIL_URBAN);

			if (match(path, "industrial"))
				return Compartment.of(
					type, SubCompartment.SOIL_INDUSTRIAL);

			return Compartment.of(type, SubCompartment.UNSPECIFIED);
		} // soil emissions

		// water emissions
		if (match(path, "emission", "water")) {
			var type = ElementaryFlowType.EMISSIONS_TO_WATER;

			if (match(path, "fossil"))
				return Compartment.of(
					type, SubCompartment.WATER_FOSSIL);

			if (match(path, "ground", "long", "term"))
				return Compartment.of(
					type, SubCompartment.WATER_GROUND_LONG_TERM);

			if (match(path, "ground"))
				return Compartment.of(
					type, SubCompartment.WATER_GROUND);

			if (match(path, "lake"))
				return Compartment.of(
					type, SubCompartment.WATER_LAKE);

			if (match(path, "ocean"))
				return Compartment.of(
					type, SubCompartment.WATER_LAKE);

			if (match(path, "river"))
				return Compartment.of(
					type, SubCompartment.WATER_RIVER);

			if (match(path, "river", "long", "term"))
				return Compartment.of(
					type, SubCompartment.WATER_RIVER_LONG_TERM);

			return Compartment.of(type, SubCompartment.UNSPECIFIED);
		} // water emissions

		if (match(path, "land")) {
			return Compartment.of(
				ElementaryFlowType.RESOURCES, SubCompartment.RESOURCES_LAND);
		}

		return null;
	}

	/**
	 * Try to find the compartment pair directly from the given compartment and
	 * sub-compartment name.
	 */
	private static Compartment find(String compartment, String subCompartment) {
		if (compartment == null)
			return null;
		var type = ElementaryFlowType.of(compartment);
		if (type == null)
			return null;
		if (Strings.isNullOrEmpty(subCompartment)
			|| "unspecified".equalsIgnoreCase(subCompartment)
			|| "(unspecified)".equalsIgnoreCase(subCompartment))
			return Compartment.of(type, SubCompartment.UNSPECIFIED);

		var sub = SubCompartment.of(subCompartment);
		return sub == null
			? null
			: Compartment.of(type, sub);
	}

	private static boolean match(String path, String... parts) {
		for (String part : parts) {
			if (!path.contains(part))
				return false;
		}
		return true;
	}

}
