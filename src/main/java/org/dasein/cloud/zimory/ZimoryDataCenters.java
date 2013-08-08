/**
 * Copyright (C) 2012 enStratus Networks Inc
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */

package org.dasein.cloud.zimory;

import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.compute.VmState;
import org.dasein.cloud.dc.DataCenter;
import org.dasein.cloud.dc.DataCenterServices;
import org.dasein.cloud.dc.Region;
import org.dasein.cloud.util.APITrace;
import org.dasein.cloud.util.Cache;
import org.dasein.cloud.util.CacheLevel;
import org.dasein.util.uom.time.Hour;
import org.dasein.util.uom.time.TimePeriod;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

/**
 * Implements data center services for Zimory describing the different Zimory regions. This class maps
 * Zimory data centers to Dasein Cloud regions. Zimory regions are ignored.
 * <p>Created by George Reese: 10/25/12 7:18 PM</p>
 * @author George Reese
 * @version 2012.09 initial version
 * @since 2012.09
 */
public class ZimoryDataCenters implements DataCenterServices {
    static private final Logger logger = Zimory.getLogger(ZimoryDataCenters.class);

    private Zimory provider;

    ZimoryDataCenters(@Nonnull Zimory provider) { this.provider = provider; }

    @Override
    public @Nullable DataCenter getDataCenter(@Nonnull String dataCenterId) throws InternalException, CloudException {
        for( Region region : listRegions() ) {
            for( DataCenter dc : listDataCenters(region.getProviderRegionId()) ) {
                if( dataCenterId.equals(dc.getProviderDataCenterId()) ) {
                    return dc;
                }
            }
        }
        return null;
    }

    @Override
    public @Nonnull String getProviderTermForDataCenter(@Nonnull Locale locale) {
        return "data center";
    }

    @Override
    public @Nonnull String getProviderTermForRegion(@Nonnull Locale locale) {
        return "region";
    }

    @Override
    public @Nullable Region getRegion(@Nonnull String providerRegionId) throws InternalException, CloudException {
        for( Region r : listRegions() ) {
            if( providerRegionId.equals(r.getProviderRegionId()) ) {
                return r;
            }
        }
        return null;
    }

    @Override
    public @Nonnull Collection<DataCenter> listDataCenters(@Nonnull String providerRegionId) throws InternalException, CloudException {
        Region r = getRegion(providerRegionId);

        if( r == null ) {
            throw new CloudException("No such region: " + providerRegionId);
        }
        DataCenter dc = new DataCenter();

        dc.setActive(r.isActive());
        dc.setAvailable(r.isAvailable());
        dc.setName(r.getName());
        dc.setProviderDataCenterId(providerRegionId);
        dc.setRegionId(providerRegionId);
        return Collections.singletonList(dc);
    }

    @Override
    public Collection<Region> listRegions() throws InternalException, CloudException {
        APITrace.begin(provider, "listRegions");
        try {
            ProviderContext ctx = provider.getContext();

            if( ctx == null ) {
                throw new NoContextException();
            }
            Cache<Region> cache = Cache.getInstance(provider, "regions", Region.class, CacheLevel.CLOUD_ACCOUNT, new TimePeriod<Hour>(10, TimePeriod.HOUR));
            Collection<Region> regions = (Collection<Region>)cache.get(ctx);

            if( regions != null ) {
                return regions;
            }
            regions = new ArrayList<Region>();

            ZimoryMethod method = new ZimoryMethod(provider);

            Document xml = method.getObject("clouds");

            if( xml == null ) {
                logger.error("Unable to communicate with the Zimory clouds endpoint");
                throw new CloudException("Could not communicate with the Zimory clouds endpoint");
            }
            NodeList clouds = xml.getElementsByTagName("cloud");

            for( int i=0; i<clouds.getLength(); i++ ) {
                Region r = toRegion(method, clouds.item(i));

                if( r != null ) {
                    regions.add(r);
                }

            }
            cache.put(ctx, regions);
            return regions;
        }
        finally {
            APITrace.end();
        }
    }

    private @Nullable Region toRegion(@Nonnull ZimoryMethod method, @Nullable Node xml) throws CloudException, InternalException {
        if( xml == null ) {
            return null;
        }
        NodeList attributes = xml.getChildNodes();
        String providerId = null, locationId = null;

        for( int i=0; i<attributes.getLength(); i++ ) {
            Node attribute = attributes.item(i);

            if( attribute.getNodeName().equalsIgnoreCase("providerId") && attribute.hasChildNodes() ) {
                providerId = attribute.getFirstChild().getNodeValue().trim();
            }
            else if( attribute.getNodeName().equalsIgnoreCase("locationId") && attribute.hasChildNodes() ) {
                locationId = attribute.getFirstChild().getNodeValue().trim();
            }
        }
        if( providerId == null || locationId == null ) {
            return null;
        }
        Document doc = method.getObject("constants/locations/" + locationId);

        if( doc == null ) {
            logger.error("Unable to communicate with the Zimory locations endpoint");
            throw new CloudException("Could not communicate with the Zimory locations endpoint");
        }
        NodeList locations = doc.getElementsByTagName("location");
        String description = null;

        for( int i=0; i<locations.getLength(); i++ ) {
            Node location = locations.item(i);

            if( location.hasChildNodes() ) {
                attributes = location.getChildNodes();
                for( int j=0; j<attributes.getLength(); j++ ) {
                    Node attribute = attributes.item(j);

                    if( attribute.getNodeName().equalsIgnoreCase("description") && attribute.hasChildNodes() ) {
                        description = attribute.getFirstChild().getNodeValue().trim();
                    }
                }
            }
        }
        doc = method.getObject("constants/providers/" + providerId);

        if( doc == null ) {
            logger.error("Unable to communicate with the Zimory providers endpoint");
            throw new CloudException("Could not communicate with the Zimory providers endpoint");
        }
        NodeList providers = doc.getElementsByTagName("provider");
        String name = null;

        for( int i=0; i<providers.getLength(); i++ ) {
            Node provider = providers.item(i);

            if( provider.hasChildNodes() ) {
                attributes = provider.getChildNodes();
                for( int j=0; j<attributes.getLength(); j++ ) {
                    Node attribute = attributes.item(j);

                    if( attribute.getNodeName().equalsIgnoreCase("name") && attribute.hasChildNodes() ) {
                        name = attribute.getFirstChild().getNodeValue().trim();
                    }
                }
            }
        }
        Region region = new Region();

        region.setActive(true);
        region.setAvailable(true);
        region.setJurisdiction("EU");
        if( name == null && description == null ) {
            region.setName(locationId + ":" + providerId);
        }
        else if( name == null ) {
            region.setName(description);
        }
        else if( description == null ) {
            region.setName(name);
        }
        else {
            region.setName(name + " - " + description);
        }
        region.setProviderRegionId(locationId + ":" + providerId);

        if( description != null && description.startsWith("eu") ) {
            region.setJurisdiction("EU");
        }
        return region;
    }
}
