package no.rutebanken.extime.loader;

import no.rutebanken.extime.util.ExtimeException;
import org.entur.netex.NetexParser;
import org.entur.netex.index.api.NetexEntitiesIndex;
import org.entur.netex.index.impl.NetexEntitiesIndexImpl;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class NetexDatasetLoader {

    public NetexEntitiesIndex load(InputStream timetableDataset) {
        try (ZipInputStream zipInputStream = new ZipInputStream(timetableDataset)) {
            NetexEntitiesIndex index = new NetexEntitiesIndexImpl();
            parseDataset(zipInputStream, index);
            return index;
        } catch (IOException e) {
            throw new ExtimeException("Error while parsing the NeTEx timetable dataset", e);
        }
    }

    /**
     * Parse a zip file containing a NeTEx archive.
     *
     * @param zipInputStream a stream on a NeTEx zip archive.
     * @param index in memory netex model
     * @throws IOException if the zip file cannot be read.
     */
    private void parseDataset(ZipInputStream zipInputStream,
                              NetexEntitiesIndex index) throws IOException {
        //NOSONAR
        ZipEntry zipEntry = zipInputStream.getNextEntry();
        NetexParser netexParser = new NetexParser();
        while (zipEntry != null) {
            byte[] allBytes = zipInputStream.readAllBytes();
            netexParser.parse(new ByteArrayInputStream(allBytes), index);
            //NOSONAR
            zipEntry = zipInputStream.getNextEntry();
        }
    }
}
