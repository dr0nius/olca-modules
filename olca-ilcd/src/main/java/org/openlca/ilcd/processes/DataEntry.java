
package org.openlca.ilcd.processes;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;

import org.openlca.ilcd.commons.DataSetReference;
import org.openlca.ilcd.commons.Other;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DataEntryByType", propOrder = {
		"timeStamp",
		"formats",
		"originalDataSet",
		"documentor",
		"useApprovals",
		"other"
})
public class DataEntry implements Serializable {

	private final static long serialVersionUID = 1L;

	@XmlElement(namespace = "http://lca.jrc.it/ILCD/Common")
	public XMLGregorianCalendar timeStamp;

	/** Describes the format of the data set. */
	@XmlElement(namespace = "http://lca.jrc.it/ILCD/Common", name = "referenceToDataSetFormat")
	public final List<DataSetReference> formats = new ArrayList<>();

	@XmlElement(namespace = "http://lca.jrc.it/ILCD/Common", name = "referenceToConvertedOriginalDataSetFrom")
	public DataSetReference originalDataSet;

	@XmlElement(namespace = "http://lca.jrc.it/ILCD/Common", name = "referenceToPersonOrEntityEnteringTheData")
	public DataSetReference documentor;

	@XmlElement(namespace = "http://lca.jrc.it/ILCD/Common", name = "referenceToDataSetUseApproval")
	public final List<DataSetReference> useApprovals = new ArrayList<>();

	@XmlElement(namespace = "http://lca.jrc.it/ILCD/Common")
	public Other other;

	@XmlAnyAttribute
	public final Map<QName, String> otherAttributes = new HashMap<>();

}
