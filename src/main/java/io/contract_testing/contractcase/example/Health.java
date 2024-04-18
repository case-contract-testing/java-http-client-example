package io.contract_testing.contractcase.example;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

@Data
class Health {

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private String status;

  Health() {
  }

  Health(String status) {
    this.status = status;
  }
}