package link.gomining.cs341.a20120666_proj5;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.util.Scanner;
import java.util.concurrent.CancellationException;

interface MyDelegate {
    void updateProgress(int progress);
    void onFinishied();
}
public class MainActivity extends AppCompatActivity implements MyDelegate {

    MyDelegate myDelegate =this;

    //For ContentResolver / ActivityResult
    private static final int CODE_READ = 42;

    //Message Max Length
    private static final int MSG_MAX = 1024*1024*10;

    //UIs
    ProgressBar mProgressBar;
    EditText mEditTextIp;
    EditText mEditTextPort;
    TextView mTextViewInputFilePath;
    EditText mEditTextOutputFilename;
    TextView mTextViewDebugOutput;

    //Enumerable for Crypt
    enum Crypt{
        Encrypt, Decrypt, None;
        public static Crypt fromId(int id){
            switch(id) {
                case R.id.radioEncrypt: return Encrypt;
                case R.id.radioDecrypt: return Decrypt;
                default: return None;
            }
        }
        public static Crypt fromByte(byte b) {
            switch(b) {
                case 0: return Crypt.Encrypt;
                case 1: return Crypt.Decrypt;
                default: return Crypt.None;
            }
        }
        public byte toByte() throws InvalidKeyException{
            if(this==Encrypt) return 0;
            else if(this==Decrypt) return 1;
            else throw new InvalidKeyException("Crypt value is none");
        }
    }
    Crypt mCrypt;
    byte mShift=1;
    String mIp;
    int mPort;
    Uri mInputUri;
    String mOutputFileName;

    //Async Task
    MyClientTask mMyClientTask=null;

    //Message
    private class Message {
        public Crypt mCrypt;
        public byte mShift;
        public int mLength;
        public String mData;
        public Message(Crypt crypt, byte shift, String data) {
            mCrypt=crypt;
            mShift=shift;
            mData=data;
            mLength=data.length();
        }
        public Message(byte[] bytes) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            mCrypt = Crypt.fromByte(buffer.get());
            mShift = buffer.get();
            buffer.getShort();
            mLength=buffer.getInt();
            if(mLength>MSG_MAX-9 || mLength<0) {
                int t = mLength;
                mLength=-1;
                throw new InvalidParameterException("Invalid Length : " + t);
            }

            try {
                byte[] dataArray = new byte[buffer.remaining()];
                buffer.get(dataArray);
                mData = new String(dataArray,"US-ASCII");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        //Build bytes of messages
        public byte[] getBytes() {
            ByteBuffer buffer = ByteBuffer.allocate(8+mLength);
            try {
                if(mLength>MSG_MAX-9 || mLength < 0)
                    throw new InvalidParameterException("Too long length : " + mLength);
                buffer.put(mCrypt.toByte());
                buffer.put(mShift);
                buffer.putShort((short)-1);
                buffer.putInt(mLength); //Endian already concerened
                buffer.put(mData.getBytes());
                return buffer.array();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
                return null;
            }
        }
        public String toString() {
            StringBuilder builder= new StringBuilder();
            builder.append(String.format("crypt : " + mCrypt.toString() + "\n"));
            builder.append(String.format("shift : " + mShift + "\n"));
            builder.append(String.format("length : " + mLength + "\n"));
            builder.append(String.format("data : " + mData + "\n"));
            return builder.toString();
        }
    }

    @Override
    public void updateProgress(int progress) {
        mProgressBar.setProgress(progress);
    }

    @Override
    public void onFinishied() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mProgressBar=(ProgressBar)findViewById(R.id.progress);
        mEditTextIp=(EditText)findViewById(R.id.editIp);
        mEditTextPort=(EditText)findViewById(R.id.editPort);
        mTextViewInputFilePath=(TextView)findViewById(R.id.textInputFilePath);
        mEditTextOutputFilename=(EditText)findViewById(R.id.editOutputName);
        mTextViewDebugOutput=(TextView)findViewById(R.id.textDebugOutput);

        mEditTextIp.addTextChangedListener(new TextWatcher() {
            @Override  public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mIp = s.toString();
            }
        });
        mEditTextPort.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override  public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try{
                    mPort = Integer.valueOf(s.toString());
                }catch(NumberFormatException e) {
                    mPort=-1;
                }
            }
        });
        ((RadioGroup)findViewById(R.id.radioGroupCrypt)).setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                mCrypt=Crypt.fromId(checkedId);
                mTextViewDebugOutput.setText(mCrypt.name());
            }
        });
        findViewById(R.id.btnFile).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(intent, CODE_READ);
            }
        });
        findViewById(R.id.btnConnect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mInputUri==null) {
                    Log.d("connectBtn","input : none");
                    Toast.makeText(getApplicationContext(),"No input file",Toast.LENGTH_SHORT).show();
                    return;
                }
                if(mMyClientTask!=null) {
                    if(mMyClientTask.getStatus()==AsyncTask.Status.RUNNING)
                        mMyClientTask.cancel(true);
                    mMyClientTask=null;
                    Log.d("connectBtn","task clearing");
                }
                if(mMyClientTask==null) {
                    String sendingData = readInput();
                    mMyClientTask=new MyClientTask(mIp,mPort,mCrypt,mShift,sendingData, myDelegate);
                    mProgressBar.setMax(sendingData.length());
                    mProgressBar.setProgress(0);
                    mMyClientTask.execute();
                }
            }
        });
        mEditTextOutputFilename.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mOutputFileName=String.valueOf(s);
            }
        });

        mEditTextIp.setText("143.248.56.16");
        mEditTextPort.setText("4000");
        ((RadioButton)findViewById(R.id.radioEncrypt)).setChecked(true);
        mCrypt=Crypt.Encrypt;
        mTextViewInputFilePath.setText("None"); mInputUri=null;
        mEditTextOutputFilename.setText("output.txt");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        final ContentResolver cr=getContentResolver();
        Uri uri = data != null ? data.getData() : null;
        if(uri!=null) {
            Log.d("activityResult","isDocument Uri : "+uri.toString());
        }
        else {
            Log.d("activityResult","missing uri");
            return;
        }
        if(requestCode==CODE_READ) {
            mInputUri=uri;
            Cursor cursor=cr.query(mInputUri,null,null,null,null,null);
            if(cursor!=null && cursor.moveToFirst()) {
                mTextViewInputFilePath.setText(cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)));
                Log.d("activityResult",mTextViewInputFilePath.getText().toString());
            }
            cursor.close();
        }
    }

    //Read content of a file with mInputFileUri
    private String readInput() {
        if(mInputUri==null) {
            Log.d("readInput","uri is null");
            return null;
        }
        ContentResolver cr=getContentResolver();

        String readContent=null;
        try (Scanner scanner=new Scanner(cr.openInputStream(mInputUri)).useDelimiter("\n")){
            readContent=scanner.next();

            Log.d("readInput","read : " + readContent);
            mTextViewDebugOutput.setText(readContent);
        } catch (Exception e) {
            Log.d("readInput","FAILED TO READ", e);
        }
        return readContent;
    }

    //AsyncTask for Network connection
    public class MyClientTask extends AsyncTask<Void, Void, String> {
        String mAddr;
        int mPort;
        Crypt mCrypt;
        byte mShift;
        String mData;

        MyDelegate mDelegate;

        MyClientTask(String addr, int port, Crypt crypt, byte shift, String data, MyDelegate delegate) {
            mAddr=addr;
            mPort=port;
            mCrypt=crypt;
            mShift=shift;
            mData=data;
            mDelegate=delegate;
        }

        @Override
        protected String doInBackground(Void... params) {
            int inputLength = mData.length();
            StringBuilder responseBuilder = new StringBuilder();
            try {
                int remainedLength, pos;
                while(true) { //create and send messages until all mData sent.
                    pos = responseBuilder.length();
                    mDelegate.updateProgress(pos);
                    remainedLength = inputLength - pos;
                    if(remainedLength <= 0)
                        break;
                    int sendingMessageDataLength = remainedLength > (MSG_MAX - 8) ? (MSG_MAX - 8) : remainedLength;
//                    Log.d("myClientTask","responseBuilder : " + responseBuilder.toString());

                    Message sendingMessage = new Message(mCrypt, mShift, mData.substring(pos, pos + sendingMessageDataLength));
                    ByteBuffer receivedBuffer = ByteBuffer.allocate(8 + sendingMessageDataLength);
                    try (Socket socket = new Socket(mAddr, mPort)) {
                        {// Send
                            try {
                                OutputStream os = socket.getOutputStream();
                                os.write(sendingMessage.getBytes());
                                Log.d("myClientTask", "sent");
                            } catch (SocketException e) {
                                Log.d("myClientTask", "sending Exception");
                                throw e;
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        {// Recv Response
                            boolean isHeaderReceived=false;
                            int receivedMessageBytesLength = 0;
                            socket.setSoTimeout(100);
                            try (InputStream inputStream = socket.getInputStream()) {
                                byte[] bytes = new byte[1024];
                                while (true) {
                                    if (isCancelled())
                                        throw new CancellationException("MyClientTask cancelled");
                                    try {
                                        int bytesRead = inputStream.read(bytes);
                                        Log.d("received", "bytes Read : " + bytesRead);
                                        if (bytesRead >= 0) {
                                            for (int i = 0; i < bytesRead; i++)
                                                receivedBuffer.put(bytes[i]);
                                            receivedMessageBytesLength += bytesRead;
                                        } else {
                                            Log.d("connection", "read failed. Connection lost");
                                            break;
                                        }
                                    } catch (SocketTimeoutException e) {
                                        Log.d("connection", "timeout");
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                                if (isHeaderReceived == false && receivedMessageBytesLength >= 8) // Header (8 bytes) received.
                                    isHeaderReceived= true;
                            } catch (IOException e) { // getInputStream fails
                                e.printStackTrace();
                            }
                            if(isCancelled())
                                throw new CancellationException("MyClientTask cancelled");
                            if(isHeaderReceived==true) {
                                byte[] receivedBytes = new byte[receivedMessageBytesLength];
                                receivedBuffer.rewind();
                                receivedBuffer.get(receivedBytes, 0, receivedMessageBytesLength);
                                Message receivedMessage = new Message(receivedBytes);
//                                Log.d("remainedLength",remainedLength+"");
//                                Log.d("receivedMessage Length",receivedMessage.mLength+"");
                                if(remainedLength == receivedMessage.mLength) {
                                    Log.d("myClientTask", "length matched");
                                    responseBuilder.append(receivedMessage.mData);
                                }
                            }
                            else {
                                Log.d("myClientTask","header receiving failed");
                            }
                        }
                    } catch (SocketTimeoutException e) {
                        Log.d("connection", "connection timeout");
                    } catch (SocketException e) {
                        e.printStackTrace();
                        Log.d("myClientTask", "socket exception");
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    } catch (IOException e) { // Socket constructor failed
                        e.printStackTrace();
                    }
                }
            } catch (CancellationException e) {
                Log.d("myClientTask","Cancelled");
                return null;
            }
            Log.d("myClientTask","end");
            return responseBuilder.toString();
        }

        @Override
        protected void onPostExecute(String resultString) {
            if(resultString==null)
                return;
            Log.d("myClientTask","result : " + resultString);
//            Toast.makeText(getApplicationContext(),"Response Saved",Toast.LENGTH_SHORT);

            Toast.makeText(getApplicationContext(),"Response Saved to " + mOutputFileName,Toast.LENGTH_SHORT).show();
            if(mOutputFileName!=null) {
                String dirname = Environment.getExternalStorageDirectory().getAbsolutePath();
                File dir = new File(dirname);
                dir.mkdirs();
                File outputFile = new File(dir,mOutputFileName);
//            String outputFilePath = dirname + "/" + mOutputFileName;

                try {
                    if (!outputFile.exists()) {
                        outputFile.createNewFile();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try(FileOutputStream fos = new FileOutputStream(outputFile,false)) {
                    fos.write(resultString.getBytes());
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Log.d("output",outputFile.getAbsolutePath());
                MediaScannerConnection.scanFile(getApplicationContext(),
                        new String[]{outputFile.getAbsolutePath()},null,null);
            }
            super.onPostExecute(null);
        }
    }
}