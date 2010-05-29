/***************************************************************
 This implementation provides a framework to publish processes to the
web through the  OGC Web Processing Service interface. The framework 
is extensible in terms of processes and data handlers. It is compliant 
to the WPS version 0.4.0 (OGC 05-007r4). 

 Copyright (C) 2006 by con terra GmbH

 Authors: 
	Theodor Foerster, ITC, Enschede, the Netherlands
	Carsten Priess, Institute for geoinformatics, University of
Muenster, Germany


 Contact: Albert Remke, con terra GmbH, Martin-Luther-King-Weg 24,
 48155 Muenster, Germany, 52n@conterra.de

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 version 2 as published by the Free Software Foundation.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program (see gnu-gpl v2.txt); if not, write to
 the Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 Boston, MA  02111-1307, USA or visit the web page of the Free
 Software Foundation, http://www.fsf.org.

 Created on: 13.06.2006
 ***************************************************************/
package org.n52.wps.io.datahandler.xml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import net.opengis.examples.packet.GMLPacketDocument;
import net.opengis.examples.packet.PropertyType;
import net.opengis.examples.packet.StaticFeatureType;
import net.opengis.examples.packet.PropertyType.Value;
import net.opengis.gml.CoordType;
import net.opengis.gml.LineStringPropertyType;
import net.opengis.gml.LinearRingMemberType;
import net.opengis.gml.LinearRingType;
import net.opengis.gml.PointPropertyType;
import net.opengis.gml.PolygonPropertyType;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.geotools.feature.DefaultFeatureCollections;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.n52.wps.io.data.binding.complex.GTVectorDataBinding;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;


/**
 * This parser handles xml files compliant to gmlpacket.xsd 
 * @author foerster
 *
 */
public class SimpleGMLParser extends AbstractXMLParser {
	
	private static Logger LOGGER = Logger.getLogger(SimpleGMLParser.class);
	private SimpleFeatureType type;
	private SimpleFeatureBuilder featureBuilder;
	
	public SimpleGMLParser() {
		super();
	}
	
	public SimpleGMLParser(boolean pReadWPSConfig) {
		super(pReadWPSConfig);
	}


	public GTVectorDataBinding parseXML(String gml) {
		GMLPacketDocument doc;
		try {
			doc = GMLPacketDocument.Factory.parse(gml);
		} catch (XmlException e) {
			throw new IllegalArgumentException("Error while parsing XML string: " + e.getMessage(), e);
		}
		if(doc != null) {
			return parseXML(doc);
		}
		return null;
	}
	
	public GTVectorDataBinding parseXML(InputStream stream) {
		GMLPacketDocument doc;
		try {
			doc = GMLPacketDocument.Factory.parse(stream);
		}
		catch(XmlException e) {
			throw new IllegalArgumentException("Error while parsing XML", e);
		}
		catch(IOException e) {
			throw new IllegalArgumentException("Error transfering XML", e);
		}
		if(doc != null) {
			return parseXML(doc);
		}
		return null;
	}
	
	private GTVectorDataBinding parseXML(GMLPacketDocument doc) {
		FeatureCollection collection = DefaultFeatureCollections.newCollection();
		int numberOfMembers = doc.getGMLPacket().getPacketMemberArray().length;
		for(int i = 0; i< numberOfMembers; i++) {
			StaticFeatureType feature = doc.getGMLPacket().getPacketMemberArray(i).getStaticFeature();
			//at the start create the featureType and the featureBuilder
			if(i==0) {
				type = createFeatureType(feature);
				featureBuilder = new SimpleFeatureBuilder(type);
			}
				
			SimpleFeature newFeature = convertStaticFeature(feature);
			if (newFeature != null) {
				collection.add(newFeature);
			}
			else {
				LOGGER.debug("feature has no geometry, feature will not be included in featureCollection");
			}
		}
		return new GTVectorDataBinding(collection); 
	}
	
	private SimpleFeature convertStaticFeature(StaticFeatureType staticFeature) {
		
		SimpleFeature feature = null;
		Geometry geom = null;
		if(staticFeature.isSetLineStringProperty()) {
			geom = convertToJTSGeometry(staticFeature.getLineStringProperty());
		}
		else if(staticFeature.isSetPointProperty()) {
			geom = convertToJTSGeometry(staticFeature.getPointProperty());
		}
		else if(staticFeature.isSetPolygonProperty()) {
			geom = convertToJTSGeometry(staticFeature.getPolygonProperty());
		}
		if(geom == null) {
			return null;
		}
		
		if(type.getAttributeCount()>1){
			if(staticFeature.sizeOfPropertyArray() > 0){
				ArrayList<Object> properties = new ArrayList<Object>(staticFeature.sizeOfPropertyArray());
				properties.add(geom);
				for (int i = 0; i < staticFeature.sizeOfPropertyArray(); i++) {						
					PropertyType ptype = staticFeature.getPropertyArray(i);
					if(!ptype.getPropertyName().contains("geom")){
					Value v = ptype.getValue();
					properties.add(v.getStringValue());	
					}
				}
				feature = featureBuilder.buildFeature(staticFeature.getFid(), properties.toArray());
			}				
		
		}
		else {
		 feature = featureBuilder.buildFeature(staticFeature.getFid(), new Object[]{geom});
			}
		
		return feature;
	}
	
	private SimpleFeatureType createFeatureType(StaticFeatureType staticFeature) {
		SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
		typeBuilder.setName("gmlPacketFeatures");
		
		if(staticFeature.isSetLineStringProperty()) {
			typeBuilder.add( "LineString", LineString.class);
			
		}
		else if(staticFeature.isSetPointProperty()) {
			typeBuilder.add( "Point", Point.class);
		}
		else if(staticFeature.isSetPolygonProperty()) {
			typeBuilder.add( "Polygon", Polygon.class);
		}
		
		if(staticFeature.sizeOfPropertyArray() > 0){
			for (int i = 0; i < staticFeature.sizeOfPropertyArray(); i++) {
				
				PropertyType type = staticFeature.getPropertyArray(i);
				if(!type.getPropertyName().contains("geom")) {
					typeBuilder.add(type.getPropertyName(),String.class);
				}
			}
			
		}
		return typeBuilder.buildFeatureType();
	}
	
	private Geometry convertToJTSGeometry(LineStringPropertyType lineString) {
		Geometry geom;
		if(lineString.getLineString().getCoordArray().length != 0) {
			CoordType[] xmlCoords = lineString.getLineString().getCoordArray();
			Coordinate[] coords = convertToJTSCoordinates(xmlCoords);
			if(coords.length == 0) {
				LOGGER.debug("feature does not include any geometry (LineString)");
				return null;
			}
			geom = geomFactory.createLineString(coords);
		}
		else if (lineString.getLineString().isSetCoordinates()) {
			throw new IllegalArgumentException("Element gml:coordinates is not supported yet");
		}
		else {
			LOGGER.debug("LineString has no coordinates");
			return null;
		}
		return geom;
	}
	
	private Geometry convertToJTSGeometry(PointPropertyType point) {
		Coordinate coord = convertToJTSCoordinate(point.getPoint().getCoord());
		return geomFactory.createPoint(coord);
	}

	private Geometry convertToJTSGeometry(PolygonPropertyType polygon) {
		LinearRingType outerRing = polygon.getPolygon().getOuterBoundaryIs().getLinearRing();
		LinearRing jtsOuterRing = convertToJTSLinearRing(outerRing);
		LinearRingMemberType[] innerRings = polygon.getPolygon().getInnerBoundaryIsArray();
		List<LinearRing> jtsInnerRings = new ArrayList<LinearRing>();
		for(LinearRingMemberType ring : innerRings) {
			if(ring.getLinearRing() != null) {
				jtsInnerRings.add(convertToJTSLinearRing(ring.getLinearRing()));
			}
		}
		return geomFactory.createPolygon(jtsOuterRing, (LinearRing[])jtsInnerRings.toArray(new LinearRing[jtsInnerRings.size()]));
	}
	
	private LinearRing convertToJTSLinearRing(LinearRingType linearRing) {
		Coordinate[] coords = convertToJTSCoordinates(linearRing.getCoordArray());
		return geomFactory.createLinearRing(coords);
	}
	
	/**
	 * expects Coordinates with X & Y or X & Y & Z
	 * @param coords
	 * @return
	 */
	private Coordinate[] convertToJTSCoordinates(CoordType[] coords) {
		List<Coordinate> coordList = new ArrayList<Coordinate>();
		for(CoordType coord : coords) {
			Coordinate coordinate = convertToJTSCoordinate(coord);
			coordList.add(coordinate);
		}
		return coordList.toArray(new Coordinate[coordList.size()]);
	}
	
	private Coordinate convertToJTSCoordinate(CoordType coord) {
		if(!coord.isSetZ()) {
			return new Coordinate(coord.getX().doubleValue(), coord.getY().doubleValue());
		}
		else {
			return new Coordinate(coord.getX().doubleValue(), 
										coord.getY().doubleValue(), 
										coord.getZ().doubleValue());
		}
	}

	public GTVectorDataBinding parse(InputStream input, String mimeType) {
		return parseXML(input);
	}

	public boolean isSupportedEncoding(String encoding) {
		return true;
	}

	
	public Class[] getSupportedInternalOutputDataType() {
		Class[] supportedClasses = {GTVectorDataBinding.class};
		return supportedClasses;
	
	}

	
}
