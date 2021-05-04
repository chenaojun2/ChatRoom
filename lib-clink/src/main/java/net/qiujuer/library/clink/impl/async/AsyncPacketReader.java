package net.qiujuer.library.clink.impl.async;

import net.qiujuer.library.clink.core.Frame;
import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.SendPacket;
import net.qiujuer.library.clink.core.ds.BytePriorityNode;
import net.qiujuer.library.clink.frames.AbsSendPacketFrame;
import net.qiujuer.library.clink.frames.CancelSendFrame;
import net.qiujuer.library.clink.frames.SendEntityFrame;
import net.qiujuer.library.clink.frames.SendHeaderFrame;

import java.io.Closeable;
import java.io.IOException;

public class AsyncPacketReader implements Closeable {

    private final PacketProvider provider;
    private volatile IoArgs args = new IoArgs();

    private volatile BytePriorityNode<Frame> node;
    private volatile int nodeSize = 0;

    private short lastIdentifier = 0;

    AsyncPacketReader(PacketProvider provider) {
        this.provider = provider;
    }

    void cancel(SendPacket packet) {
        synchronized (this) {
            if (nodeSize == 0) {
                return;
            }

            for (BytePriorityNode<Frame> x = node, before = null; x != null; before = x, x = x.next) {
                Frame frame = x.item;
                if (frame instanceof AbsSendPacketFrame) {
                    AbsSendPacketFrame packetFrame = (AbsSendPacketFrame) frame;
                    if (packetFrame.getPacket() == packet) {
                        boolean removeable = packetFrame.abort();
                        if (removeable) {
                            removeFrame(x, before);
                            if (packetFrame instanceof SendHeaderFrame) {
                                break;
                            }
                        }

                        CancelSendFrame cancelSendFrame = new CancelSendFrame(packetFrame.getBodyIdentifier());
                        appendNewFrame(cancelSendFrame);

                        provider.completedPacket(packet, false);
                        break;
                    }
                }
            }
        }

    }

    boolean requestTakePacket() {
        synchronized (this) {
            if (nodeSize >= 1) {
                return true;
            }
        }

        SendPacket packet = provider.takePacket();
        if (packet != null) {
            short identifier = generateIdentifier();
            SendHeaderFrame frame = new SendHeaderFrame(identifier, packet);
            appendNewFrame(frame);
        }

        synchronized (this) {
            return nodeSize != 0;
        }
    }


    @Override
    public synchronized void close() {
        while (node != null) {
            Frame frame = node.item;
            if (frame instanceof AbsSendPacketFrame) {
                SendPacket packet = ((AbsSendPacketFrame) frame).getPacket();
                provider.completedPacket(packet, false);
            }
        }

        nodeSize = 0;
        node = null;
    }


    IoArgs fillData() {
        Frame currentFrame = getCurrentFrame();
        if (currentFrame == null) {
            return null;
        }
        try {
            if (currentFrame.handle(args)) {
                //消费完成
                //基于当前桢，构建后面的桢
                Frame nextFrame = currentFrame.nextFrame();
                if (nextFrame != null) {
                    appendNewFrame(nextFrame);
                } else if (currentFrame instanceof SendEntityFrame) {
                    provider.completedPacket(((SendEntityFrame) currentFrame).getPacket(), true);
                }

                popCurrentFrame();
            }

            return args;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


    private synchronized void appendNewFrame(Frame frame) {
        BytePriorityNode<Frame> newNode = new BytePriorityNode<>(frame);
        if (node != null) {
            node.appendWithPriority(newNode);
        } else {
            node = newNode;
        }
        nodeSize++;
    }

    private Frame getCurrentFrame() {
        if(node == null) {
            return null;
        }
        return node.item;
    }

    private synchronized void popCurrentFrame() {
        node = node.next;
        nodeSize--;
        if(node == null) {
            requestTakePacket();
        }
    }

    private void removeFrame(BytePriorityNode<Frame> removeNode, BytePriorityNode<Frame> before) {
        if(before == null) {
            node = removeNode.next;
        } else {
            before.next = removeNode.next;
        }

        nodeSize--;
        if(node == null) {
            requestTakePacket();
        }
    }

    private short generateIdentifier() {
        short identifier = ++lastIdentifier;
        if (identifier == 255) {
            identifier = 0;
        }
        return identifier;
    }

    interface PacketProvider {

        SendPacket takePacket();

        void completedPacket(SendPacket packet, boolean isSucceed);
    }
}
