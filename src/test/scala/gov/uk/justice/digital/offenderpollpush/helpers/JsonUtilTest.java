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
        OffenderDetail detail = new OffenderDetail(11l, new IDs("CRN11"));

        final String str = JsonUtil.toString(detail);

        assertEquals("{\"offenderId\":11,\"crn\":\"CRN11\"}", str);
    }

    @Test
    public void testToObject() throws IOException {

        final String filePath = "src/test/resources/offenderDetail.json";
        final String content = new String (Files.readAllBytes( Paths.get(filePath) ));

        final OffenderDetail offenderDetail = JsonUtil.readValue(content, OffenderDetail.class);

        assertEquals(11, offenderDetail.getOffenderId().longValue());
        assertEquals("CRN11", offenderDetail.getCrn());
    }

}
