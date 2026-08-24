package com.example.pop.nps;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ResponderNpsRequest(
        @NotNull @Min(0) @Max(10) Integer nota,
        String observacao) {
}
