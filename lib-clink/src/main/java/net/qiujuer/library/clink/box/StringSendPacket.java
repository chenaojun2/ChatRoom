package net.qiujuer.library.clink.box;

import net.qiujuer.library.clink.core.SendPacket;

import java.io.ByteArrayInputStream;

public class StringSendPacket extends BytesSendPacket{

    public StringSendPacket(String msg) {
       super(msg.getBytes());
    }

    @Override
    public byte type() {
        return TYPE_MEMORY_STRING;
    }
}
