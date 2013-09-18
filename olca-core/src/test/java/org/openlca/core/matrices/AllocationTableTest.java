package org.openlca.core.matrices;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.openlca.core.TestSession;
import org.openlca.core.database.IDatabase;
import org.openlca.core.matrix.CalcExchange;
import org.openlca.core.matrix.LongPair;
import org.openlca.core.matrix.ProductIndex;
import org.openlca.core.matrix.cache.AllocationCache;
import org.openlca.core.model.AllocationMethod;

public class AllocationTableTest {

	private IDatabase database = TestSession.getDefaultDatabase();

	@Test
	public void testDefaultFactor() {
		ProductIndex index = new ProductIndex(
				LongPair.of(999999999, 999999999), 1);
		AllocationCache table = new AllocationCache(database, index,
				AllocationMethod.USE_DEFAULT);
		double f = table.getFactor(index.getRefProduct(), new CalcExchange());
		assertEquals(1.0, f, 1e-16);
	}

}
