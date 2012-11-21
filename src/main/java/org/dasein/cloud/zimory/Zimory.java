package org.dasein.cloud.zimory;

import org.apache.log4j.Logger;
import org.dasein.cloud.AbstractCloud;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.zimory.compute.ZimoryCompute;
import org.dasein.cloud.zimory.network.ZimoryNetwork;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.text.ParseException;
import java.text.SimpleDateFormat;

/**
 * Support for the Zimory cloud. This implementation owes a lot to the work done by the jclouds team
 * in prior support for Dasein Cloud Zimory. Though the Dasein Cloud native version is done from
 * scratch, it would not have been possible so quickly without their help.
 * <p>Created by George Reese: 10/25/12 6:30 PM</p>
 * @author George Reese
 * @version 2012.09 initial version
 * @since 2012.09
 */
public class Zimory extends AbstractCloud {
    static private final Logger logger = getLogger(Zimory.class);

    static public class AccountOwner {
        public String userId;
        public String login;
    }

    static private @Nonnull String getLastItem(@Nonnull String name) {
        int idx = name.lastIndexOf('.');

        if( idx < 0 ) {
            return name;
        }
        else if( idx == (name.length()-1) ) {
            return "";
        }
        return name.substring(idx+1);
    }

    static public @Nonnull Logger getLogger(@Nonnull Class<?> cls) {
        String pkg = getLastItem(cls.getPackage().getName());

        if( pkg.equals("zimory") ) {
            pkg = "";
        }
        else {
            pkg = pkg + ".";
        }
        return Logger.getLogger("dasein.cloud.zimory.std." + pkg + getLastItem(cls.getName()));
    }

    static public @Nonnull Logger getWireLogger(@Nonnull Class<?> cls) {
        return Logger.getLogger("dasein.cloud.zimory.wire." + getLastItem(cls.getPackage().getName()) + "." + getLastItem(cls.getName()));
    }

    static public String escapeXml(String nonxml) {
        StringBuilder str = new StringBuilder();

        for( int i=0; i<nonxml.length(); i++ ) {
            char c = nonxml.charAt(i);

            switch( c ) {
                case '&': str.append("&amp;"); break;
                case '>': str.append("&gt;"); break;
                case '<': str.append("&lt;"); break;
                case '"': str.append("&quot;"); break;
                case '[': str.append("&#091;"); break;
                case ']': str.append("&#093;"); break;
                case '!': str.append("&#033;"); break;
                default: str.append(c);
            }
        }
        return str.toString();
    }

    public Zimory() { }

    public @Nonnull AccountOwner getAccountOwner() throws CloudException, InternalException {
        ProviderContext ctx = getContext();

        if( ctx == null ) {
            throw new NoContextException();
        }
        ZimoryMethod method = new ZimoryMethod(this);

        Document doc = method.getObject("accounts/" + ctx.getAccountNumber());

        if( doc == null ) {
            throw new CloudException("Unable to identify accounts endpoint");
        }
        NodeList accounts = doc.getElementsByTagName("account");

        if( accounts.getLength() < 1 ) {
            throw new CloudException("Unable to identify the account owner");
        }
        Node account = accounts.item(0);
        AccountOwner owner = new AccountOwner();

        NodeList attributes = account.getChildNodes();

        for( int j=0; j<attributes.getLength(); j++ ) {
            Node a = attributes.item(j);

            if( a.getNodeName().equalsIgnoreCase("userAccountOwner") ) {
                NodeList uaList = a.getChildNodes();

                for( int k=0; k<uaList.getLength(); k++ ) {
                    Node ua = uaList.item(k);

                    if( ua.getNodeName().equalsIgnoreCase("login") && ua.hasChildNodes() ) {
                        owner.login = ua.getFirstChild().getNodeValue().trim();
                    }
                    else if( ua.getNodeName().equals("id") && ua.hasChildNodes() ) {
                        owner.userId = ua.getFirstChild().getNodeValue().trim();
                    }
                }
            }
        }
        if( owner.userId == null || owner.login == null ) {
            throw new CloudException("Unable to identify the account owner");
        }
        return owner;
    }

    @Override
    public @Nonnull String getCloudName() {
        ProviderContext ctx = getContext();
        String name = (ctx == null ? null : ctx.getCloudName());

        return (name == null ? "Zimory" : name);
    }

    @Override
    public @Nonnull ZimoryCompute getComputeServices() {
        return new ZimoryCompute(this);
    }

    @Override
    public @Nonnull ZimoryDataCenters getDataCenterServices() {
        return new ZimoryDataCenters(this);
    }

    @Override
    public @Nonnull ZimoryNetwork getNetworkServices() {
        return new ZimoryNetwork(this);
    }

    @Override
    public @Nonnull String getProviderName() {
        ProviderContext ctx = getContext();
        String name = (ctx == null ? null : ctx.getProviderName());

        return (name == null ? "Zimory" : name);
    }

    public @Nonnegative long parseTimestamp(@Nullable String date) throws CloudException {
        //"createDate":"2012-02-25T17:34:22-06:00"
        if( date == null || date.equals("") ) {
            return 0L;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

        try {
            return fmt.parse(date).getTime();
        }
        catch( ParseException e ) {
            if( date.length() > 6 ) {
                char c = date.charAt(date.length()-6);

                if( c == '-' || c == '+' ) {
                    date = date.substring(0, date.length()-6) + "GMT" + date.substring(date.length()-6);
                    fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

                    try {
                        return fmt.parse(date).getTime();
                    }
                    catch( ParseException again ) {
                        throw new CloudException("Could not parse date: " + date);
                    }
                }
            }
            throw new CloudException("Could not parse date: " + date);
        }
    }

    @Override
    public @Nullable String testContext() {
        if( logger.isTraceEnabled() ) {
            logger.trace("ENTER - " + Zimory.class.getName() + ".testContext()");
        }
        try {
            ProviderContext ctx = getContext();

            if( ctx == null ) {
                logger.warn("No context was provided for testing");
                return null;
            }
            try {
                ZimoryMethod method = new ZimoryMethod(this);
                Document doc = method.getObject("accounts");

                if( doc == null ) {
                    return null;
                }
                NodeList accounts = doc.getElementsByTagName("account");

                if( accounts.getLength() == 1 ) {
                    Node account = accounts.item(0);
                    NodeList attributes = account.getChildNodes();

                    for( int i=0; i<attributes.getLength(); i++ ) {
                        Node a = attributes.item(i);

                        if( a.getNodeName().equalsIgnoreCase("accountId") && a.hasChildNodes() ) {
                            return a.getFirstChild().getNodeValue().trim();
                        }
                    }
                    return null;
                }
                else {
                    String accountId = null;

                    for( int i=0; i<accounts.getLength(); i++ ) {
                        NodeList attributes = accounts.item(i).getChildNodes();
                        String id = null, login = null;

                        for( int j=0; j<attributes.getLength(); j++ ) {
                            Node a = attributes.item(j);

                            if( a.getNodeName().equalsIgnoreCase("accountId") && a.hasChildNodes() ) {
                                id = a.getFirstChild().getNodeValue().trim();
                            }
                            else if( a.getNodeName().equalsIgnoreCase("userAccountOwner") && a.hasChildNodes() ) {
                                NodeList uaList = a.getChildNodes();

                                for( int k=0; k<uaList.getLength(); k++ ) {
                                    Node ua = uaList.item(k);

                                    if( ua.getNodeName().equalsIgnoreCase("login") && ua.hasChildNodes() ) {
                                        login = ua.getFirstChild().getNodeValue().trim();
                                    }
                                }
                            }
                        }
                        if( ctx.getAccountNumber().equalsIgnoreCase(login) || ctx.getAccountNumber().equals(id) ) {
                            return id;
                        }
                        else if( accountId == null ) {
                            accountId = id;
                        }
                    }
                    return accountId;
                }
            }
            catch( Throwable t ) {
                logger.error("Error testing Zimory credentials for " + ctx.getAccountNumber() + ": " + t.getMessage());
                return null;
            }
        }
        finally {
            if( logger.isTraceEnabled() ) {
                logger.trace("EXIT - " + Zimory.class.getName() + ".textContext()");
            }
        }
    }
}