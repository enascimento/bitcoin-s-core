package org.bitcoins.core.serializers

import org.bitcoins.core.currency.Satoshis
import org.bitcoins.core.number.Int64

/**
  * Created by chris on 6/23/16.
  */
trait RawSatoshisSerializer extends RawBitcoinSerializer[Satoshis] {

  def read(bytes : List[Byte]): Satoshis = Satoshis(Int64(bytes))

  def write(satoshis: Satoshis): String = satoshis.underlying.hex
}

object RawSatoshisSerializer extends RawSatoshisSerializer
