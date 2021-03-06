package org.bitcoins.core.crypto

import org.bitcoins.core.number.UInt32
import org.bitcoins.core.protocol.script.ScriptPubKey
import org.bitcoins.core.protocol.transaction.Transaction
import org.bitcoins.core.script.flag.ScriptFlag

/**
 * Created by chris on 4/6/16.
 * Represents a transaction whose input is being checked against the spending conditions of the
 * scriptPubKey
 */
trait TransactionSignatureComponent {

  /**
   * The transaction being checked for the validity of signatures
 *
   * @return
   */
  def transaction : Transaction

  /**
   * The index of the input whose script signature is being checked
 *
   * @return
   */
  def inputIndex : UInt32

  /**
   * The script signature being checked
 *
   * @return
   */
  def scriptSignature = transaction.inputs(inputIndex.toInt).scriptSignature
  /**
   * The scriptPubKey for which the input is being checked against
 *
   * @return
   */
  def scriptPubKey : ScriptPubKey

  /**
   * The flags that are needed to verify if the signature is correct
 *
   * @return
   */
  def flags : Seq[ScriptFlag]
}

object TransactionSignatureComponent {

  private sealed case class TransactionSignatureComponentImpl(transaction : Transaction, inputIndex : UInt32,
                                                              scriptPubKey : ScriptPubKey, flags : Seq[ScriptFlag]) extends TransactionSignatureComponent

  def apply(transaction : Transaction, inputIndex : UInt32, scriptPubKey : ScriptPubKey,
            flags : Seq[ScriptFlag]) : TransactionSignatureComponent = {
    TransactionSignatureComponentImpl(transaction,inputIndex,scriptPubKey, flags)
  }

  /**
    * This factory method is used for changing the scriptPubKey inside of a txSignatureComponent
    *
    * @param oldTxSignatureComponent
    * @param scriptPubKey
    * @return
    */
  def apply(oldTxSignatureComponent : TransactionSignatureComponent, scriptPubKey : ScriptPubKey) : TransactionSignatureComponent = {
    TransactionSignatureComponentImpl(oldTxSignatureComponent.transaction,
      oldTxSignatureComponent.inputIndex,scriptPubKey, oldTxSignatureComponent.flags)
  }

}