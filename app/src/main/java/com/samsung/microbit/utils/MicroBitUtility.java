package com.samsung.microbit.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

class MicroBitUtility
{
  public enum Result
  {
    None,
    BleError,
    V2Only,
    NoService,
    Protocol,
    NoData,
    OutOfMemory,
    Finished
  }

  private static final int requestTypeNone = 0;
  private static final int requestTypeLogLength = 1;  // reply data = 4 bytes log data length
  private static final int requestTypeLogRead = 2;    // reply data = up to 19 bytes of log data

  private static final int requestLogHTMLHeader = 0;
  private static final int requestLogHTML = 1;
  private static final int requestLogCSV = 2;

  private static final int jobLowMAX = 0x0E;
  private static final int jobLowERR = 0x0F;     // reply data = 4 bytes signed integer error

  int  m_Job;
  int  m_JobLow;

  int m_Request;
  int m_Length;
  int m_Batch;
  int m_Index;
  int m_BatchLength;
  int m_BatchReceived;
  int m_Received;

  ByteBuffer m_Data;

  /**
   * Callback interface for client
   */
  public interface Client {
      MicroBitUtility.Result microbitUtilityWriteCharacteristic( final byte [] data, int dataLength);
  }
  private MicroBitUtility.Client mClient;

  public MicroBitUtility( MicroBitUtility.Client client) {
    super();
    mClient = client;
    m_Job = 0;
    m_JobLow = 0;
    m_Request = requestTypeNone;
    m_Index = 0;
    m_Length = 0;
    m_Received = 0;
    m_Batch = 4;          // If > 4, nearly every batch has to wait for a free slot
    m_BatchLength = 0;
    m_BatchReceived = 0;
    m_Data = null;
  }

  public ByteBuffer resultData()   { return m_Data; }
  public long       resultLength() { return m_Length; }

  public MicroBitUtility.Result startLogDownload() {
    m_Index = 0;
    m_Length = 0;
    m_Received = 0;

    m_Request = requestTypeLogLength;
    m_Job += 0x10;
    if ( m_Job == 0x100)
      m_Job = 0;
    m_JobLow = 0;
    //    typedef struct requestLog_t
    //    {
    //        uint8_t  job;
    //        uint8_t  type;                  // requestType_t
    //        uint8_t  format;                // requestLogFormat
    //    } requestLog_t;
    int requestSize = 3;
    byte [] request = new byte[ requestSize];
    Arrays.fill( request, (byte) 0);
    request[ 0] = (byte) m_Job;
    request[ 1] = (byte) m_Request;
    request[ 2] = (byte) requestLogHTML;
    return mClient.microbitUtilityWriteCharacteristic( request, requestSize);
  }

  public MicroBitUtility.Result process( final byte [] reply, int replyLength) {
    //    typedef struct reply_t
    //    {
    //        uint8_t  job;                   // Service cycles low nibble i.e client job + { 0x00, 0x01, 0x02, ..., 0x0E, 0x00, ... }
    //                                        // low nibble == 0x0F (jobLowERR) indicates error and data = 4 bytes signed integer error
    //        uint8_t  data[19];
    //    } reply_t;

    if ( replyLength == 0)
      return MicroBitUtility.Result.Protocol;

    int replyJob = reply[0];

    if ( ( replyJob & 0xF0) != m_Job)
      return MicroBitUtility.Result.None; // Ignore if not the current job

    if ( ( replyJob & 0x0F) == jobLowERR)
      return MicroBitUtility.Result.Protocol; // Reply is error

    if ( ( replyJob & 0x0F) != m_JobLow)
      return MicroBitUtility.Result.Protocol; // Reply is out of order

    m_JobLow++;
    if ( m_JobLow == 0x0F)
      m_JobLow = 0;

    int dataLength = replyLength - 1;
    byte [] data = new byte[ dataLength];
    System.arraycopy( reply, 1, data, 0, dataLength);

    MicroBitUtility.Result result = MicroBitUtility.Result.None;

    switch ( m_Request)
    {
      case requestTypeLogLength:
        result = logLengthProcess( data, dataLength);
        break;
      case requestTypeLogRead:
        result = logReadProcess( data, dataLength);
        break;
    }
    return result;
  }

  public float progress() {
    float progress = (float) m_Index / m_Length;
    if ( progress < 0) progress = 0;
    if ( progress > 1) progress = 1;
    return progress;
  }

  public MicroBitUtility.Result logRead() {
    m_BatchReceived = 0;
    m_BatchLength = m_Length - m_Received;
    if ( m_BatchLength > 19 * m_Batch)
      m_BatchLength = 19 * m_Batch;

    m_Request = requestTypeLogRead;
    m_Job += 0x10;
    if ( m_Job == 0x100)
      m_Job = 0;
    m_JobLow = 0;
    //    typedef struct requestLogRead_t
    //    {
    //        uint8_t  job;
    //        uint8_t  type;                  // requestType_t
    //        uint8_t  format;                // requestLogFormat
    //        uint8_t  reserved;              // set to zero
    //        uint32_t index;                 // index into data
    //        uint32_t batchlen;              // size in bytes to return
    //        uint32_t length;                // length of whole file, from requestTypeLogLength
    //    } requestLogRead_t;
    int requestSize = 16;
    byte [] request = new byte[ requestSize];
    Arrays.fill( request, (byte) 0);
    request[ 0] = (byte) m_Job;
    request[ 1] = (byte) m_Request;
    request[ 2] = (byte) requestLogHTML;
    request[ 3] = (byte) 0;
    byte [] index = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt( m_Index).array();
    System.arraycopy( index, 0, request, 4, 4);
    byte [] batchlen = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt( m_BatchLength).array();
    System.arraycopy( batchlen, 0, request, 8, 4);
    byte [] length = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt( m_Length).array();
    System.arraycopy( length, 0, request, 12, 4);
    return mClient.microbitUtilityWriteCharacteristic( request, requestSize);
  }

  public MicroBitUtility.Result logLengthProcess( final byte [] data, int dataLength) {
    if ( dataLength < 4)
      return MicroBitUtility.Result.Protocol;

    try {
      ByteBuffer bb = ByteBuffer.allocate(4);
      bb.put(data, 0, 4);
      bb.rewind();
      m_Length = bb.order(ByteOrder.LITTLE_ENDIAN).getInt();
      if (m_Length == 0)
        return MicroBitUtility.Result.NoData;

      m_Data = ByteBuffer.allocate(m_Length);
    } catch ( Exception e) {
      return MicroBitUtility.Result.OutOfMemory;
    }

    m_Index = 0;
    m_Received = 0;
    return logRead();
  }

  public MicroBitUtility.Result logReadProcess( final byte [] data, int dataLength) {
    if ( dataLength < 1)
      return MicroBitUtility.Result.Protocol;

    if ( m_Data.remaining() < m_Length - m_Received)
      return MicroBitUtility.Result.Protocol;

    m_Data.put( data, 0, dataLength);
    m_Received += dataLength;
    m_BatchReceived += dataLength;

    if ( m_BatchReceived == m_BatchLength)
    {
      m_Index += m_BatchReceived;

      if ( m_Received == m_Length)
        return MicroBitUtility.Result.Finished;

      return logRead();
    }

    return MicroBitUtility.Result.None;
  }
}
