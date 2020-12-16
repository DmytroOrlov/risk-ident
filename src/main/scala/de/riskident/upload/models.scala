package de.riskident.upload

import cats.Show

case class SourceLine(
    id: String,
    produktId: String,
    name: String,
    description: String,
    price: BigDecimal,
    stock: Int,
)

case class DestLine(
    produktId: String,
    name: String,
    description: String,
    price: BigDecimal,
    stockSum: Int,
)

object DestLine {
  implicit val `Show[DestLine]`: Show[DestLine] = Show.show {
    case l@DestLine(produktId, name, description, price, stockSum) =>
      (produktId :: name :: description :: price :: stockSum :: Nil).mkString("|")
  }
}