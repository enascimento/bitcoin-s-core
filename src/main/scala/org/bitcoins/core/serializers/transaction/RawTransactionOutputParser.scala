package org.bitcoins.core.serializers.transaction

import org.bitcoins.core.currency.{CurrencyUnits, Satoshis}
import org.bitcoins.core.number.UInt64
import org.bitcoins.core.serializers.{RawBitcoinSerializer, RawSatoshisSerializer}
import org.bitcoins.core.serializers.script.{RawScriptPubKeyParser, ScriptParser}
import org.bitcoins.core.protocol.CompactSizeUInt
import org.bitcoins.core.protocol.transaction.{TransactionOutput, TransactionOutputImpl}
import org.bitcoins.core.util.{BitcoinSLogger, BitcoinSUtil}
import org.slf4j.LoggerFactory

import scala.annotation.tailrec

/**
 * Created by chris on 1/11/16.
 * https://bitcoin.org/en/developer-reference#txout
 */
trait RawTransactionOutputParser extends RawBitcoinSerializer[Seq[TransactionOutput]] with ScriptParser with BitcoinSLogger {

  override def read(bytes : List[Byte]) : Seq[TransactionOutput] = {
    val numOutputs = bytes.head.toInt
    @tailrec
    def loop(bytes : List[Byte], accum : List[TransactionOutput], outputsLeftToParse : Int) : List[TransactionOutput] = {
      if (outputsLeftToParse > 0) {
        //TODO: this needs to be refactored to, need to create a function that returns a single TransactionOutput
        //then call that function multiple times to get a Seq[TransactionOutput]
        val satoshisHex = BitcoinSUtil.encodeHex(bytes.take(8).reverse)
        logger.debug("Satoshi hex: " + satoshisHex)
        val satoshis = parseSatoshis(satoshisHex)
        //it doesn't include itself towards the size, thats why it is incremented by one
        val firstScriptPubKeyByte = 8
        val scriptCompactSizeUIntSize : Int = CompactSizeUInt.parseCompactSizeUIntSize(bytes(firstScriptPubKeyByte)).toInt
        logger.debug("VarInt hex: " + BitcoinSUtil.encodeHex(bytes.slice(firstScriptPubKeyByte,firstScriptPubKeyByte + scriptCompactSizeUIntSize)))
        val scriptSigCompactSizeUInt : CompactSizeUInt =
          CompactSizeUInt.parseCompactSizeUInt(bytes.slice(firstScriptPubKeyByte,firstScriptPubKeyByte + scriptCompactSizeUIntSize))

        val scriptPubKeyBytes = bytes.slice(firstScriptPubKeyByte + scriptCompactSizeUIntSize,
          firstScriptPubKeyByte + scriptCompactSizeUIntSize + scriptSigCompactSizeUInt.num.toInt)
        val scriptPubKey = RawScriptPubKeyParser.read(scriptPubKeyBytes)
        val parsedOutput = TransactionOutput(satoshis,scriptPubKey)
        val newAccum =  parsedOutput :: accum
        val bytesToBeParsed = bytes.slice(parsedOutput.size, bytes.size)
        val outputsLeft = outputsLeftToParse-1
        logger.debug("Parsed output: " + parsedOutput)
        logger.debug("Outputs left to parse: " + outputsLeft)
        loop(bytesToBeParsed, newAccum, outputsLeft)
      } else accum
    }
    loop(bytes.tail,List(),numOutputs).reverse
  }

  override def write(outputs : Seq[TransactionOutput]) : String = {
    val numOutputs = CompactSizeUInt(UInt64(outputs.length))
    val serializedOutputs : Seq[String] = for {
      output <- outputs
    } yield write(output)
    numOutputs.hex + serializedOutputs.mkString
  }


  /**
   * Writes a single transaction output
 *
   * @param output
   * @return
   */
  def write(output : TransactionOutput) : String = {
    val satoshis = CurrencyUnits.toSatoshis(output.value)
    val compactSizeUIntHex = output.scriptPubKeyCompactSizeUInt.hex
    val satoshisHexWithoutPadding : String = BitcoinSUtil.flipEndianness(satoshis.hex)
    val satoshisHex = addPadding(16,satoshisHexWithoutPadding)
    logger.debug("compactSizeUIntHex: " + compactSizeUIntHex)
    logger.debug("satoshis: " + satoshisHex)
    if (compactSizeUIntHex == "00") satoshisHex + compactSizeUIntHex
    else satoshisHex + compactSizeUIntHex + output.scriptPubKey.hex
  }

  /**
    * Parses the amount of satoshis a hex string represetns
 *
    * @param hex the hex string that is being parsed to satoshis
    * @return the value of the hex string in satoshis
    */
  private def parseSatoshis(hex : String) : Satoshis = RawSatoshisSerializer.read(hex)
}


object RawTransactionOutputParser extends RawTransactionOutputParser