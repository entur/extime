package no.rutebanken.extime.job;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the URL and the method, which are what an operator types, and the 409 an overlapping request gets.
 *
 * <p>Standalone rather than {@code @WebMvcTest}: Boot 4 moved that slice into a separate
 * {@code spring-boot-webmvc-test} dependency, and booting it here failed on a missing {@code PubSubTemplate}.
 * A standalone setup needs neither the dependency nor a context to pin three status codes.
 */
class TimetableExportControllerTest {

    private final TimetableExportJob timetableExportJob = mock(TimetableExportJob.class);
    private final MockMvc mockMvc =
            MockMvcBuilders.standaloneSetup(new TimetableExportController(timetableExportJob)).build();

    @Test
    void postingToTheEndpointRunsAnExport() throws Exception {
        when(timetableExportJob.export()).thenReturn("a-correlation-id");

        mockMvc.perform(post("/services/timetable/export"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("a-correlation-id")));

        verify(timetableExportJob).export();
    }

    @Test
    void anOverlappingRequestGetsAConflict() throws Exception {
        when(timetableExportJob.export()).thenThrow(new ExportAlreadyRunningException());

        mockMvc.perform(post("/services/timetable/export"))
                .andExpect(status().isConflict());
    }

    /**
     * POST only, so that a crawler, a browser or a stray link cannot publish a dataset.
     */
    @Test
    void theEndpointIsNotReachableWithGet() throws Exception {
        mockMvc.perform(get("/services/timetable/export"))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(timetableExportJob);
    }
}
