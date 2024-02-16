package com.enonic.app.booster.storage;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.common.io.ByteSource;

import com.enonic.app.booster.CacheItem;
import com.enonic.xp.data.PropertyTree;
import com.enonic.xp.node.DeleteNodeParams;
import com.enonic.xp.node.Node;
import com.enonic.xp.node.NodeId;
import com.enonic.xp.node.NodeNotFoundException;
import com.enonic.xp.node.NodePath;
import com.enonic.xp.node.NodeService;
import com.enonic.xp.util.BinaryReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentCaptor.captor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeCacheStoreTest
{
    @Mock
    NodeService nodeService;

    @Test
    void generateCacheKey()
    {
        final NodeCacheStore nodeCacheStore = new NodeCacheStore( nodeService );
        final String cacheKey = nodeCacheStore.generateCacheKey( "https://example.com/" );
        assertEquals( "0f115db062b7c0dd030b16878c99dea5", cacheKey );
    }

    @Test
    void remove()
    {
        final NodeCacheStore nodeCacheStore = new NodeCacheStore( nodeService );
        nodeCacheStore.remove( "0f115db062b7c0dd030b16878c99dea5" );

        final ArgumentCaptor<DeleteNodeParams> captor = captor();
        verify( nodeService ).delete( captor.capture() );
        assertEquals( NodeId.from( "0f115db062b7c0dd030b16878c99dea5" ), captor.getValue().getNodeId() );
    }

    @Test
    void remove_failsafe()
    {
        final NodeCacheStore nodeCacheStore = new NodeCacheStore( nodeService );
        when( nodeService.delete( any() ) ).thenThrow( NodeNotFoundException.class );
        assertDoesNotThrow( () -> nodeCacheStore.remove( "0f115db062b7c0dd030b16878c99dea5" ) );

        final ArgumentCaptor<DeleteNodeParams> captor = captor();
        verify( nodeService ).delete( captor.capture() );
        assertEquals( NodeId.from( "0f115db062b7c0dd030b16878c99dea5" ), captor.getValue().getNodeId() );
    }


    @Test
    void get_not_found()
    {
        final NodeCacheStore nodeCacheStore = new NodeCacheStore( nodeService );
        NodeId nodeId = NodeId.from( "0f115db062b7c0dd030b16878c99dea5" );

        when( nodeService.getById( nodeId ) ).thenThrow( NodeNotFoundException.class );

        CacheItem result = nodeCacheStore.get( "0f115db062b7c0dd030b16878c99dea5" );

        assertNull( result );
    }

    @Test
    void get()
    {
        final NodeCacheStore nodeCacheStore = new NodeCacheStore( nodeService );
        NodeId nodeId = NodeId.from( "0f115db062b7c0dd030b16878c99dea5" );
        Node.Builder nodeBuilder = Node.create().id( nodeId ).name( "0f115db062b7c0dd030b16878c99dea5" ).parentPath( NodePath.ROOT );

        PropertyTree data = new PropertyTree();
        data.addSet( "headers" ).addString( "header1", "value1" );
        data.addString( "contentType", "text/html" );
        data.addLong( "contentLength", 1234L );
        data.addString( "etag", "1234567890" );
        data.addString( "url", "https://example.com/" );
        data.addInstant( "cachedTime", Instant.now() );
        data.addInstant( "invalidatedTime", Instant.now() );

        nodeBuilder.data( data );

        Node node = nodeBuilder.build();

        when( nodeService.getById( nodeId ) ).thenReturn( node );
        when( nodeService.getBinary( nodeId, BinaryReference.from( "data.gzip" ) ) ).thenReturn( ByteSource.empty() );
        when( nodeService.getBinary( nodeId, BinaryReference.from( "data.br" ) ) ).thenReturn( ByteSource.empty() );

        CacheItem result = nodeCacheStore.get( "0f115db062b7c0dd030b16878c99dea5" );

        assertNotNull( result );
        assertEquals( "text/html", result.contentType() );
        assertEquals( 1234, result.contentLength() );
        assertEquals( "1234567890", result.etag() );
        assertEquals( "https://example.com/", result.url() );
        assertNotNull( result.cachedTime() );
        assertNotNull( result.invalidatedTime() );
        assertNotNull( result.gzipData() );
        assertNotNull( result.brotliData() );
        assertEquals( result.headers(), Map.of( "header1", List.of( "value1" ) ) );

    }

    @Test
    void get_brotli_optional()
    {
        final NodeCacheStore nodeCacheStore = new NodeCacheStore( nodeService );
        NodeId nodeId = NodeId.from( "0f115db062b7c0dd030b16878c99dea5" );
        Node.Builder nodeBuilder = Node.create().id( nodeId ).name( "0f115db062b7c0dd030b16878c99dea5" ).parentPath( NodePath.ROOT );

        PropertyTree data = new PropertyTree();
        data.addSet( "headers" ).addString( "header1", "value1" );
        data.addString( "contentType", "text/html" );
        data.addLong( "contentLength", 1234L );
        data.addString( "etag", "1234567890" );
        data.addString( "url", "https://example.com/" );
        data.addInstant( "cachedTime", Instant.now() );
        data.addInstant( "invalidatedTime", Instant.now() );

        nodeBuilder.data( data );

        Node node = nodeBuilder.build();

        when( nodeService.getById( nodeId ) ).thenReturn( node );
        when( nodeService.getBinary( nodeId, BinaryReference.from( "data.gzip" ) ) ).thenReturn( ByteSource.empty() );
        when( nodeService.getBinary( nodeId, BinaryReference.from( "data.br" ) ) ).thenReturn( null );

        CacheItem result = nodeCacheStore.get( "0f115db062b7c0dd030b16878c99dea5" );

        assertNotNull( result );
        assertEquals( "text/html", result.contentType() );
        assertEquals( 1234, result.contentLength() );
        assertEquals( "1234567890", result.etag() );
        assertEquals( "https://example.com/", result.url() );
        assertNotNull( result.cachedTime() );
        assertNotNull( result.invalidatedTime() );
        assertNotNull( result.gzipData() );
        assertNull( result.brotliData() );
        assertEquals( result.headers(), Map.of( "header1", List.of( "value1" ) ) );
    }
}
