package org.example.repository.dal.controller.VO;

import lombok.Data;

@Data
public class ChatMessageVO {
    String id;
    Integer index;
    ChatMessage chatMessage;
}
