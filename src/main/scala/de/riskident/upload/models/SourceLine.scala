package de.riskident.upload.models

case class SourceLine(
    id: String,
    produktId: String,
    name: String,
    description: String,
    price: BigDecimal,
    stock: Int,
)
