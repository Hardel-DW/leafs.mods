package fr.hardel.leafs.chunk.disk;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class PendingWriteTest {
    private final AtomicInteger photos = new AtomicInteger();
    private final PendingWrite write = new PendingWrite(this::photo);

    private CompoundTag photo() {
        photos.incrementAndGet();
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", 4882);
        return tag;
    }

    @Test
    void thePhotoAnswersUntilTheBytesExist() throws IOException {
        CompoundTag first = write.read();
        assertEquals(4882, first.getIntOr("DataVersion", 0));
        assertEquals(1, photos.get());

        write.compressed(CompressedChunk.of(first));
        CompoundTag second = write.read();

        assertEquals(first, second);
        assertNotSame(first, second);
        assertEquals(1, photos.get());
    }

    @Test
    void theScanFollowsTheSameRule() throws IOException {
        CollectFields fromPhoto = new CollectFields(new FieldSelector(IntTag.TYPE, "DataVersion"));
        write.scan(fromPhoto);
        assertEquals(4882, ((CompoundTag) fromPhoto.getResult()).getIntOr("DataVersion", 0));

        write.compressed(CompressedChunk.of(photo()));
        CollectFields fromBytes = new CollectFields(new FieldSelector(IntTag.TYPE, "DataVersion"));
        write.scan(fromBytes);
        assertEquals(4882, ((CompoundTag) fromBytes.getResult()).getIntOr("DataVersion", 0));
    }
}
