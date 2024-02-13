package com.enonic.app.booster.storage;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.ByteSource;

import com.enonic.app.booster.CacheItem;
import com.enonic.app.booster.MessageDigests;
import com.enonic.app.booster.io.ByteSupply;
import com.enonic.xp.data.PropertySet;
import com.enonic.xp.data.PropertyTree;
import com.enonic.xp.node.CreateNodeParams;
import com.enonic.xp.node.DeleteNodeParams;
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

    public static final BinaryReference GZIP_DATA_BINARY_REFERENCE = BinaryReference.from( "data.gzip" );

    public static final BinaryReference BROTLI_DATA_BINARY_REFERENCE = BinaryReference.from( "data.br" );

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

            final var headers = adaptHeaders( node.data().getSet( "headers" ).toMap() );

            final String contentType = node.data().getString( "contentType" );
            final int contentLength = node.data().getLong( "contentLength" ).intValue();
            final String etag = node.data().getString( "etag" );
            final String url = node.data().getString( "url" );
            final Instant cachedTime = node.data().getInstant( "cachedTime" );
            final Instant invalidatedTime = node.data().getInstant( "invalidatedTime" );
            final ByteSource gzipBody;
            ByteSource brotliBody = null;
            try
            {
                gzipBody = nodeService.getBinary( nodeId, GZIP_DATA_BINARY_REFERENCE );
            }
            catch ( NodeNotFoundException e )
            {
                LOG.warn( "Cached node does not have response body attachment {}", nodeId );
                return null;
            }

            try
            {
                brotliBody = nodeService.getBinary( nodeId, BROTLI_DATA_BINARY_REFERENCE );
            }
            catch ( NodeNotFoundException e )
            {
                LOG.warn( "Cached node does not have brotli attachment {}", nodeId );
            }

            return new CacheItem( url, contentType, headers, cachedTime, invalidatedTime, contentLength, etag, ByteSupply.of( gzipBody ),
                                  brotliBody == null ? null : ByteSupply.of( brotliBody ) );
        } );
    }

    public void put( final String cacheKey, String repo, CacheItem cacheItem )
    {
        final Instant now = Instant.now();

        final NodeId nodeId = NodeId.from( cacheKey );

        final ByteSource gzipByteSource = ByteSupply.asByteSource( cacheItem.gzipData() );

        final ByteSource brotliByteSource = cacheItem.brotliData() == null ? null : ByteSupply.asByteSource( cacheItem.brotliData() );

        BoosterContext.runInContext( () -> {
            final PropertyTree data = new PropertyTree();
            data.setString( "url", cacheItem.url() );
            data.setString( "contentType", cacheItem.contentType() );
            data.setLong( "contentLength", (long) cacheItem.contentLength() );
            data.setString( "etag", cacheItem.etag() );
            data.setBinaryReference( "gzipData", GZIP_DATA_BINARY_REFERENCE );
            if ( brotliByteSource != null )
            {
                data.setBinaryReference( "brotliData", BROTLI_DATA_BINARY_REFERENCE );
            }
            data.setString( "repo", repo );
            data.setInstant( "cachedTime", now );

            final PropertySet headersPropertyTree = data.newSet();
            cacheItem.headers().forEach( headersPropertyTree::addStrings );
            data.setSet( "headers", headersPropertyTree );

            if ( nodeService.nodeExists( nodeId ) )
            {
                LOG.debug( "Updating existing cache node {}", nodeId );

                try
                {
                    final UpdateNodeParams.Builder updateParams = UpdateNodeParams.create()
                        .id( nodeId )
                        .editor( editor -> editor.data = data )
                        .attachBinary( GZIP_DATA_BINARY_REFERENCE, gzipByteSource );
                    if ( brotliByteSource != null )
                    {
                        updateParams.attachBinary( BROTLI_DATA_BINARY_REFERENCE, brotliByteSource );
                    }
                    nodeService.update( updateParams.build() );
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
                    final CreateNodeParams.Builder createParams = CreateNodeParams.create()
                        .name( cacheKey )
                        .parent( NodePath.ROOT )
                        .setNodeId( nodeId )
                        .data( data )
                        .attachBinary( GZIP_DATA_BINARY_REFERENCE, gzipByteSource );
                    if ( brotliByteSource != null )
                    {
                        createParams.attachBinary( BROTLI_DATA_BINARY_REFERENCE, brotliByteSource );
                    }

                    nodeService.create( createParams.build() );
                }
                catch ( Exception e )
                {
                    LOG.debug( "Cannot create node {}", nodeId, e );
                }
            }
        } );
    }

    public void remove( final String cacheKey )
    {
        BoosterContext.runInContext( () -> {
            final NodeId nodeId = NodeId.from( cacheKey );
            try
            {
                nodeService.delete( DeleteNodeParams.create().nodeId( nodeId ).build() );
            }
            catch ( NodeNotFoundException e )
            {
                LOG.debug( "Cached node not found {}", nodeId );
            }
        } );
    }

    public String generateCacheKey( final String url )
    {
        final byte[] digest = MessageDigests.sha256().digest( ( url ).getBytes( StandardCharsets.ISO_8859_1 ) );
        final byte[] truncated = Arrays.copyOf( digest, 16 );
        return HexFormat.of().formatHex( truncated );
    }

    private Map<String, List<String>> adaptHeaders( final Map<String, Object> headers )
    {
        final LinkedHashMap<String, List<String>> result = new LinkedHashMap<>();
        for ( Map.Entry<String, Object> stringObjectEntry : headers.entrySet() )
        {
            final String key = stringObjectEntry.getKey();
            final Object value = stringObjectEntry.getValue();
            if ( value instanceof Collection )
            {
                result.put( key, List.copyOf( (Collection<String>) value ) );
            }
            else if ( value instanceof String )
            {
                result.put( key, List.of( (String) value ) );
            }
        }
        return result;
    }

}
