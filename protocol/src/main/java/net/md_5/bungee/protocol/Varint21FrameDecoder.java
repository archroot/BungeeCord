package net.md_5.bungee.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;
import ru.leymooo.botfilter.discard.DiscardUtils;
import ru.leymooo.botfilter.discard.ErrorStream;

public class Varint21FrameDecoder extends ByteToMessageDecoder
{

    private boolean shutDowned = false;
    public void shutdown()
    {
        shutDowned = true;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception
    {
        //BotFilter start - rewrite Varint21Decoder

        if ( !ctx.channel().isActive() || shutDowned )
        {
            super.setSingleDecode( true );
            return;
        }

        int origReaderIndex = in.readerIndex();

        int i = 3;
        while ( i-- > 0 )
        {
            if ( !in.isReadable() )
            {
                in.readerIndex( origReaderIndex );
                return;
            }

            byte read = in.readByte();
            if ( read >= 0 )
            {
                // Make sure reader index of length buffer is returned to the beginning
                in.readerIndex( origReaderIndex );
                int packetLength = DefinedPacket.readVarInt( in );

                if ( packetLength <= 0 )
                {
                    super.setSingleDecode( true );
                    shutdown();
                    DiscardUtils.discardAndClose( ctx.channel() ).addListener( (ChannelFutureListener) future ->
                    {
                        ErrorStream.error( "[" + future.channel().remoteAddress() + "] <-> Varint21FrameDecoder received invalid packet length " + packetLength + ", disconnected" );
                    } );
                    return;
                }

                if ( in.readableBytes() < packetLength )
                {
                    in.readerIndex( origReaderIndex );
                    return;
                }
                out.add( in.readRetainedSlice( packetLength ) );
                return;
            }
        }

        super.setSingleDecode( true );
        shutdown();
        DiscardUtils.discardAndClose( ctx.channel() ).addListener( (ChannelFutureListener) future ->
        {
            ErrorStream.error( "[" + future.channel().remoteAddress() + "] <-> Varint21FrameDecoder packet length field too long, disconnected" );
        } );
        //throw new CorruptedFrameException( "length wider than 21-bit" );
    }
    //BotFilter end
}
