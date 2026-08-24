package com.jiake.jk.video;

import com.jiake.jk.video.controller._public.InteractionPublicController;
import com.jiake.jk.video.pojo.response.GetDirectCommentResponse;
import com.jiake.jk.video.service.InteractionService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InteractionTest {

    @Test
    void directComment_shouldDelegateWithoutStartingExternalUserService() {
        InteractionService interactionService = mock(InteractionService.class);
        InteractionPublicController controller = new InteractionPublicController(interactionService);
        GetDirectCommentResponse comment = new GetDirectCommentResponse();
        when(interactionService.directComment(2001L, null)).thenReturn(List.of(comment));

        var result = controller.directComment(2001L, null);

        assertEquals(1, result.getData().size());
        verify(interactionService).directComment(2001L, null);
    }
}
