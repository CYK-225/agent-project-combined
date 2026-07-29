package com.cyk.task.DAL.Controller.DTO;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateTaskTO {
    String companyName;
    String taskType;
    List<String> fieldList;
    String configName;
    Boolean isUpdate;
    String webAddress;
    String userId;
    Long promptId;
}
