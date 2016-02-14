package org.scalacoin.marshallers.transaction

import org.scalacoin.marshallers.RawBitcoinSerializer
import org.scalacoin.protocol.transaction.{TransactionImpl, Transaction}
import org.scalacoin.util.{CryptoUtil, ScalacoinUtil}
import org.slf4j.LoggerFactory

/**
 * Created by chris on 1/14/16.
 * For deserializing and re-serializing a bitcoin transaction
 * https://bitcoin.org/en/developer-reference#raw-transaction-format
 */
trait RawTransactionParser extends RawBitcoinSerializer[Transaction] {

  private lazy val logger = LoggerFactory.getLogger(this.getClass().toString())

  def read(bytes : List[Byte]) = {

    val versionBytes = bytes.take(4)
    val version = Integer.parseInt(ScalacoinUtil.encodeHex(versionBytes.reverse),16)
    val txInputBytes = bytes.slice(4,bytes.size)
    val inputs = RawTransactionInputParser.read(txInputBytes)
    val inputsSize = inputs.map(_.size).sum
    logger.debug("Bytes size: " + bytes.size)
    logger.debug("Num of inputs: " + inputs.size)
    logger.debug("Input size: " + inputsSize)
    logger.debug("First input: " + inputs(0))
    logger.debug("Second input: " + inputs(1))
    val outputsStartIndex = inputsSize + 5
    val outputsBytes = bytes.slice(outputsStartIndex, bytes.size)
    logger.debug("Output start index " + outputsStartIndex)
    logger.debug("Output bytes: " + ScalacoinUtil.encodeHex(outputsBytes))
    val outputs = RawTransactionOutputParser.read(outputsBytes)
    logger.debug("Outputs: " + outputs)
    val outputsSize = outputs.map(_.size).sum
    logger.debug("Outputs size: " + outputsSize)
    val lockTimeStartIndex = outputsStartIndex + outputsSize + 1
    val lockTimeBytes = bytes.slice(lockTimeStartIndex, bytes.size)
    val lockTime = Integer.parseInt(ScalacoinUtil.encodeHex(lockTimeBytes.reverse),16)

    TransactionImpl(version,inputs,outputs,lockTime)
  }

  def write(tx : Transaction) : String = {
    //add leading zero if the version byte doesn't require two hex numbers
    val txVersionHex = tx.version.toHexString
    val versionWithoutPadding = addPrecedingZero(txVersionHex)
    val version = addPadding(8,versionWithoutPadding)
    val inputs : String = RawTransactionInputParser.write(tx.inputs)
    val outputs : String = RawTransactionOutputParser.write(tx.outputs)
    val lockTimeWithoutPadding : String = ScalacoinUtil.flipHalfByte(tx.lockTime.toHexString.reverse)
    val lockTime = addPadding(8,lockTimeWithoutPadding)
    version + inputs + outputs + lockTime
  }
}


object RawTransactionParser extends RawTransactionParser
