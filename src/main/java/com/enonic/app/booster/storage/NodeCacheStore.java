package com.enonic.app.booster.storage;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.ByteSource;

import com.enonic.app.booster.CacheItem;
import com.enonic.app.booster.MessageDigests;
import com.enonic.app.booster.io.BytesWriter;
import com.enonic.xp.data.PropertySet;
import com.enonic.xp.data.PropertyTree;
import com.enonic.xp.node.CreateNodeParams;
import com.enonic.xp.node.Node;
import com.enonic.xp.node.NodeId;
import com.enonic.xp.node.NodeNotFoundException;
import com.enonic.xp.node.NodePath;
import com.enonic.xp.node.NodeService;
import com.enonic.xp.node.UpdateNodeParams;
import com.enonic.xp.util.BinaryReference;

@Component(immediate = true, service = NodeCacheStore.class)
public class NodeCacheStore
{
    private static final Logger LOG = LoggerFactory.getLogger( NodeCacheStore.class );

    private final NodeService nodeService;

    @Activate
    public NodeCacheStore( @Reference final NodeService nodeService )
    {
        this.nodeService = nodeService;
    }

    public CacheItem get( final String cacheKey )
    {

        return BoosterContext.callInContext( () -> {

            final NodeId nodeId = NodeId.from( cacheKey );
            LOG.debug( "Accessing cached response {}", nodeId );

            final Node node;
            try
            {
                node = nodeService.getById( nodeId );
            }
            catch ( NodeNotFoundException e )
            {
                LOG.debug( "Cached node not found {}", nodeId );
                return null;
            }

            final Map<String, String[]> headers = adaptHeaders( node.data().getSet( "headers" ).toMap() );

            final String contentType = node.data().getString( "contentType" );
            final int contentLength = node.data().getLong( "contentLength" ).intValue();
            final String etag = node.data().getString( "etag" );
            final String url = node.data().getString( "url" );
            final Instant cachedTime = node.data().getInstant( "cachedTime" );
            final ByteSource body;
            try
            {
                body = nodeService.getBinary( nodeId, BinaryReference.from( "data.gzip" ) );
            }
            catch ( NodeNotFoundException e )
            {
                LOG.warn( "Cached node does not have response body attachment {}", nodeId );
                return null;
            }

            return new CacheItem( url, contentType, headers, cachedTime, contentLength, etag, BytesWriter.of( body ) );
        } );
    }

    public void put( final String cacheKey, final String fullUrl, final String contentType, final Map<String, String[]> headers, final String repo,
                     BytesWriter bytes )
    {
        final NodeId nodeId = NodeId.from( cacheKey );

        BoosterContext.runInContext( () -> {

            ByteArrayOutputStream gzipData = new ByteArrayOutputStream();
            final GZIPOutputStream gzipOutputStream = new GZIPOutputStream( gzipData );
            final DigestOutputStream digestOutputStream = new DigestOutputStream( gzipOutputStream, MessageDigests.sha256() );
            try (gzipOutputStream; digestOutputStream)
            {
                bytes.writeTo( digestOutputStream );
            }

            final PropertyTree data = new PropertyTree();
            data.setString( "url", fullUrl );
            data.setString( "contentType", contentType );
            data.setLong( "contentLength", (long) bytes.size() );
            data.setString( "etag", HexFormat.of().formatHex( Arrays.copyOf( digestOutputStream.getMessageDigest().digest(), 16 ) ) );
            data.setBinaryReference( "gzipData", BinaryReference.from( "data.gzip" ) );
            data.setString( "repo", repo );
            data.setInstant( "cachedTime", Instant.now() );
            final PropertySet headersPropertyTree = data.newSet();

            headers.forEach( headersPropertyTree::addStrings );

            data.setSet( "headers", headersPropertyTree );

            if ( nodeService.nodeExists( nodeId ) )
            {
                LOG.debug( "Updating existing cache node {}", nodeId );

                try
                {
                    nodeService.update( UpdateNodeParams.create()
                                            .id( nodeId )
                                            .editor( editor -> editor.data = data )
                                            .attachBinary( BinaryReference.from( "data.gzip" ), ByteSource.wrap( gzipData.toByteArray() ) )
                                            .build() );
                }
                catch ( Exception e )
                {
                    LOG.debug( "Cannot update node {}", nodeId, e );
                }
            }
            else
            {
                LOG.debug( "Creating new cache node {}", nodeId );
                try
                {
                    nodeService.create( CreateNodeParams.create()
                                            .parent( NodePath.ROOT )
                                            .setNodeId( nodeId )
                                            .data( data )
                                            .attachBinary( BinaryReference.from( "data.gzip" ), ByteSource.wrap( gzipData.toByteArray() ) )
                                            .name( cacheKey )
                                            .build() );
                }
                catch ( Exception e )
                {
                    LOG.debug( "Cannot create node {}", nodeId, e );
                }
            }
        } );
    }

    public String generateCacheKey( final String url )
    {
        final byte[] digest = MessageDigests.sha256().digest( ( url ).getBytes( StandardCharsets.ISO_8859_1 ) );
        final byte[] truncated = Arrays.copyOf( digest, 16 );
        return HexFormat.of().formatHex( truncated );
    }

    private Map<String, String[]> adaptHeaders( final Map<String, Object> headers )
    {
        final LinkedHashMap<String, String[]> result = new LinkedHashMap<>();
        for ( Map.Entry<String, Object> stringObjectEntry : headers.entrySet() )
        {
            final String key = stringObjectEntry.getKey();
            final Object value = stringObjectEntry.getValue();
            if ( value instanceof Collection<?> )
            {
                result.put( key, ( (Collection<String>) value ).toArray( String[]::new ) );
            }
            else if ( value instanceof String )
            {
                result.put( key, new String[]{(String) value} );
            }
        }
        return result;
    }

}
