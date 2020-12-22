package de.riskident.upload.models

import cats.Show

case class DestLine(
    produktId: String,
    name: String,
    description: String,
    price: BigDecimal,
    stockSum: Int,
)

object DestLine {
  implicit val `Show[DestLine]`: Show[DestLine] = Show.show {
    case DestLine(produktId, name, description, price, stockSum) =>
      (produktId :: name :: description :: f"$price%.2f" :: stockSum :: Nil).mkString("|")
  }
}
