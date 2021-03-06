package org.mustangproject.ZUGFeRD;
/**
 * Mustangproject's ZUGFeRD implementation
 * Neccessary interface for ZUGFeRD exporter
 * Licensed under the APLv2
 * @date 2014-05-10
 * @version 1.0
 * @author jstaerk
 * */

import java.math.BigDecimal;

public interface IZUGFeRDExportableItem {

	IZUGFeRDExportableProduct getProduct();

	/**
	 * The price of one item incl. taxes
	 * @return
	 */
	BigDecimal getPriceGross();

	/**
	 * The price of one item excl. taxes
	 * @return
	 */
	BigDecimal getPrice();

	/***
	 * how many
	 * @return
	 */
	BigDecimal getQuantity();

	/**
	 * The price of quantity items incl. taxes
	 * @return
	 */
	BigDecimal getTotalGross();

}
