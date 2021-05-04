package net.qiujuer.library.clink.impl.async;

import net.qiujuer.library.clink.core.Frame;
import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.ReceivePacket;
import net.qiujuer.library.clink.frames.*;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Collection;
import java.util.HashMap;

public class AsyncPacketWriter implements Closeable {

    private final PacketProvider provider;
    private final HashMap<Short, PacketModel> packetMap = new HashMap<>();
    private final IoArgs args = new IoArgs();
    private volatile Frame frameTemp;

    AsyncPacketWriter(PacketProvider provider) {
        this.provider = provider;
    }

    public IoArgs takeIoArgs() {
        args.limit(frameTemp == null ? Frame.FRAME_HEADER_LENGTH
                : frameTemp.getConsumableLength());
        return args;
    }

    public void consumeIoArgs(IoArgs args) {
        if (frameTemp == null) {
            Frame temp;
            do {
                temp = buildNewFrame(args);
            } while (temp == null && args.remained());

            if (temp == null) {
                return;
            }

            frameTemp = temp;
            if (!args.remained()) {
                return;
            }
        }
        Frame currentFrame = frameTemp;

        do {
            try {
                if (currentFrame.handle(args)) {
// 某帧已接收完成
                    if (currentFrame instanceof ReceiveHeaderFrame) {
                        // Packet 头帧消费完成，则根据头帧信息构建接收的Packet
                        ReceiveHeaderFrame headerFrame = (ReceiveHeaderFrame) currentFrame;
                        ReceivePacket packet = provider.takePacket(headerFrame.getPacketType(),
                                headerFrame.getPacketLength(),
                                headerFrame.getPacketHeaderInfo());
                        appendNewPacket(headerFrame.getBodyIdentifier(), packet);
                    } else if (currentFrame instanceof ReceiveEntityFrame) {
                        // Packet 实体帧消费完成，则将当前帧消费到Packet
                        completeEntityFrame((ReceiveEntityFrame) currentFrame);
                    }

                    // 接收完成后，直接推出循环，如果还有未消费数据则交给外层调度
                    frameTemp = null;
                    break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } while (args.remained());
    }

    private void completeEntityFrame(ReceiveEntityFrame frame) {
        synchronized (packetMap) {
            short identifier = frame.getBodyIdentifier();
            int length = frame.getBodyLength();
            PacketModel model = packetMap.get(identifier);
            model.unreceivedLength -= length;
            if (model.unreceivedLength <= 0) {
                provider.completedPacket(model.packet, true);
                packetMap.remove(identifier);
            }
        }
    }

    private void appendNewPacket(short identifier, ReceivePacket packet) {
        synchronized (packetMap) {
            PacketModel model = new PacketModel(packet);
            packetMap.put(identifier, model);
        }
    }

    private Frame buildNewFrame(IoArgs args) {
        AbsReceiveFrame frame = ReceiveFrameFactory.createInstance(args);
        if (frame instanceof CancelReceiveFrame) {
            cancelReceivePacket(frame.getBodyIdentifier());
            return null;
        } else if (frame instanceof ReceiveEntityFrame) {
            WritableByteChannel channel = getPacketChannel(frame.getBodyIdentifier());
            ((ReceiveEntityFrame) frame).bindPacketChannel(channel);
        }
        return frame;
    }

    private void cancelReceivePacket(short identifier) {
        synchronized (packetMap) {
            PacketModel model = packetMap.get(identifier);
            if (model != null) {
                ReceivePacket packet = model.packet;
                provider.completedPacket(packet, false);
            }
        }
    }

    private WritableByteChannel getPacketChannel(short identifier) {
        synchronized (packetMap) {
            PacketModel model = packetMap.get(identifier);
            return model == null ? null : model.channel;
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (packetMap) {
            Collection<PacketModel> values = packetMap.values();
            for (PacketModel value : values) {
                provider.completedPacket(value.packet, false);
            }
            packetMap.clear();
        }
    }


    interface PacketProvider {

        ReceivePacket takePacket(byte type, long length, byte[] headerInfo);

        void completedPacket(ReceivePacket packet, boolean isSucceed);
    }

    static class PacketModel {
        final ReceivePacket packet;
        final WritableByteChannel channel;
        volatile long unreceivedLength;

        PacketModel(ReceivePacket<?, ?> packet) {
            this.packet = packet;
            this.channel = Channels.newChannel(packet.open());
            this.unreceivedLength = packet.length();
        }
    }
}

