package org.openlca.core.database;

import java.util.ArrayList;
import java.util.List;

import org.openlca.core.model.Direction;
import org.openlca.core.model.ImpactCategory;
import org.openlca.core.model.descriptors.ImpactDescriptor;

public class ImpactCategoryDao extends
        RootEntityDao<ImpactCategory, ImpactDescriptor> {

	public ImpactCategoryDao(IDatabase database) {
		super(ImpactCategory.class, ImpactDescriptor.class, database);
	}

	@Override
	protected List<ImpactDescriptor> queryDescriptors(
			String condition, List<Object> params) {
		var sql = """
					select
						d.id,
						d.ref_id,
						d.name,
						d.description,
						d.version,
						d.last_change,
						d.f_category,
						d.library,
						d.tags,
						d.reference_unit,
						d.direction from
				""" + getEntityTable() + " d";
		if (condition != null) {
			sql += " " + condition;
		}

		var cons = descriptorConstructor();
		var list = new ArrayList<ImpactDescriptor>();
		NativeSql.on(db).query(sql, params, r -> {
			var d = cons.get();
			d.id = r.getLong(1);
			d.refId = r.getString(2);
			d.name = r.getString(3);
			d.description = r.getString(4);
			d.version = r.getLong(5);
			d.lastChange = r.getLong(6);
			var catId = r.getLong(7);
			if (!r.wasNull()) {
				d.category = catId;
			}
			d.library = r.getString(8);
			d.tags = r.getString(9);
			d.referenceUnit = r.getString(10);

			var direction = r.getString(11);
			if (direction != null) {
				d.direction = Direction.from(direction);
			}
			list.add(d);
			return true;
		});
		return list;
	}

}
