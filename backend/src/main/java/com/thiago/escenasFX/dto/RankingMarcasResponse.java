package com.thiago.escenasFX.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RankingMarcasResponse {
    private List<RankingItemDTO> mejores;
    private List<RankingItemDTO> peores;
}
