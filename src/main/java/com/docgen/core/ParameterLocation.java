package com.docgen.core;
// Localização de um parâmetro na requisição.

// // PATH identifica o recurso ou a posição específica do recurso que queremos acessar
 // query tem ? na url e acrescenta condições ou opções.
// header esta no cabeçalho http, informações adicionais sobre a requisição
// body os dados estão no corpo da requisição, quais dados você está enviando
public enum ParameterLocation {
    PATH, QUERY, HEADER, BODY
}
