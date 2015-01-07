package org.openlca.jsonld;

import java.lang.reflect.Type;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import org.openlca.core.model.ImpactCategory;
import org.openlca.core.model.ImpactMethod;
import org.openlca.core.model.ModelType;

class ImpactMethodWriter implements Writer<ImpactMethod> {

	private EntityStore store;

	public ImpactMethodWriter() {
	}

	public ImpactMethodWriter(EntityStore store) {
		this.store = store;
	}

	@Override
	public void write(ImpactMethod method) {
		if (method == null || store == null)
			return;
		if (store.contains(ModelType.IMPACT_METHOD, method.getRefId()))
			return;
		JsonObject obj = serialize(method, null, null);
		store.add(ModelType.IMPACT_METHOD, method.getRefId(), obj);
	}

	@Override
	public JsonObject serialize(ImpactMethod method, Type type,
			JsonSerializationContext context) {
		JsonObject obj = new JsonObject();
		JsonWriter.addContext(obj);
		JsonWriter.addAttributes(method, obj, store);
		JsonArray array = new JsonArray();
		for (ImpactCategory category : method.getImpactCategories()) {
			JsonObject ref = Refs.put(category, store);
			array.add(ref);
		}
		obj.add("impactCategories", array);
		// TODO: parameters
		return obj;
	}
}
