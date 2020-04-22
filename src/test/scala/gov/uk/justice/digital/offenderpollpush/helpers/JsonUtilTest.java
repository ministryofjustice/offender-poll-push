package gov.uk.justice.digital.offenderpollpush.helpers;

import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import gov.uk.justice.digital.offenderpollpush.data.OffenderDetail;
import gov.uk.justice.digital.offenderpollpush.data.OffenderDetail.IDs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

public class JsonUtilTest {

    @Test
    public void testToString() throws JsonProcessingException {
        OffenderDetail detail = new OffenderDetail(11l, new IDs("CRN11", "AN1234Z"));

        final String str = JsonUtil.toString(detail);

        assertEquals("{\"offenderId\":11,\"crn\":\"CRN11\",\"nomsNumber\":\"AN1234Z\"}", str);
    }

    @Test
    public void testToStringWithoutNomsNumber() throws JsonProcessingException {
        OffenderDetail detail = new OffenderDetail(11l, new IDs("CRN11", null));

        final String str = JsonUtil.toString(detail);

        assertEquals("{\"offenderId\":11,\"crn\":\"CRN11\",\"nomsNumber\":null}", str);
    }

    @Test
    public void testTobObject() throws IOException {

        final String filePath = "src/test/resources/offenderDetail.json";
        final String content = new String (Files.readAllBytes( Paths.get(filePath) ));

        final OffenderDetail offenderDetail = JsonUtil.readValue(content, OffenderDetail.class);

        assertEquals(11, offenderDetail.getOffenderId().longValue());
        assertEquals("CRN11", offenderDetail.getCrn());
        assertEquals("G0560UO", offenderDetail.getNomsNumber());
    }

    @Test
    public void testToObjectWithoutNomsNumber() throws IOException {

        final String filePath = "src/test/resources/offenderDetailWithoutNomsNumber.json";
        final String content = new String (Files.readAllBytes( Paths.get(filePath) ));

        final OffenderDetail offenderDetail = JsonUtil.readValue(content, OffenderDetail.class);

        assertEquals(11, offenderDetail.getOffenderId().longValue());
        assertEquals("CRN11", offenderDetail.getCrn());
        assertNull(offenderDetail.getNomsNumber());
    }

}
