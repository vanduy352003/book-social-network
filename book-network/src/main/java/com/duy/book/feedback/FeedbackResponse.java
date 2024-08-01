package com.duy.book.feedback;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NonNull
public class FeedbackResponse {
    private Double note;
    private String comment;
    private boolean ownFeedback;
}
