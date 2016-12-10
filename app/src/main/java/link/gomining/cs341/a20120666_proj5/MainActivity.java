package link.gomining.cs341.a20120666_proj5;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
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

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Scanner;

public class MainActivity extends AppCompatActivity {

    private static final int CODE_READ = 42;
    private static final int CODE_WRITE = 43;
    private static final int CODE_TREE = 44;

    ProgressBar mProgressBar;
    EditText mEditTextIp;
    EditText mEditTextPort;
//    EditText mEditTextOutputFilePath;
    TextView mTextViewInputFilePath;
    TextView mTextViewOutputDir;
    TextView mTextViewOutputFilename;
    TextView mTextViewDebugOutput;

    enum Crypt{
        encrypt, decrypt, none;
        public static Crypt fromId(int id){
            switch(id) {
                case R.id.radioEncrypt: return encrypt;
                case R.id.radioDecrypt: return decrypt;
                default: return none;
            }
        }
    }
    Crypt mCrypt;

    String mIp;
    int mPort;
    Uri mInputUri;
    Uri mOutputDirUri;
    Uri mOutputUri;
    String mOutputFileName;

    MyClientTask mMyClientTask=null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mProgressBar=(ProgressBar)findViewById(R.id.progress);
        mEditTextIp=(EditText)findViewById(R.id.editIp);
        mEditTextPort=(EditText)findViewById(R.id.editPort);
//        mEditTextOutputFilePath=(EditText)findViewById(R.id.editOutputPath);
        mTextViewInputFilePath=(TextView)findViewById(R.id.textInputFilePath);
        mTextViewOutputDir=(TextView)findViewById(R.id.textOutputDir);
        mTextViewOutputFilename=(TextView)findViewById(R.id.textOutputName);
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
                    mPort=0;
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
                Log.d("connectBtn","Ip : " + mIp);
                Log.d("connectBtn","port : " + mPort);
                if(mInputUri==null) Log.d("connectBtn", "Input Uri : null");
                else Log.d("connectBtn", "Input Uri : " + mInputUri.toString());
                Log.d("connectBtn","output File Path : " + mOutputFileName);
                Log.d("connectBtn","input : " + readInput());

//                if(mOutputUri!=null) {
                if(mMyClientTask!=null) {
                    if(mMyClientTask.getStatus()==AsyncTask.Status.RUNNING) {
                        mMyClientTask.cancel(true);
                    }
                    mMyClientTask=null;
                    Log.d("connectBtn","task stopped");
                }
                    if(mMyClientTask==null) {
                        mMyClientTask=new MyClientTask(mIp,mPort);
                        mMyClientTask.execute();
                    }
//                    ContentResolver cr=getContentResolver();
//
//                    try(OutputStream os = cr.openOutputStream(mOutputUri,"w") ){
//                        os.write("Great job".getBytes());
//                    } catch (FileNotFoundException e) {
//                        e.printStackTrace();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
            }
        });
//        findViewById(R.id.btnOutputDir).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
//                startActivityForResult(Intent.createChooser(intent, "Output Directory"), CODE_TREE);
//            }
//        });
        findViewById(R.id.btnOutputFile).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TITLE, "newFileName__(for_overwriting_tap_that_again).txt");
                startActivityForResult(intent,CODE_WRITE);
            }
        });

        mEditTextIp.setText("143.248.56.16");
        mEditTextPort.setText("4000");
//        mEditTextIp.setText("192.168.0.3");
//        mEditTextPort.setText("2012");
        ((RadioButton)findViewById(R.id.radioEncrypt)).setChecked(true);
//        mEditTextOutputFilePath.setText("output.txt");
        mTextViewInputFilePath.setText("None"); mInputUri=null;
        mTextViewOutputDir.setText("None"); mOutputDirUri=null;
        mTextViewOutputFilename.setText("None"); mOutputFileName=null;
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
//                if(mInputUri!=null) {
//                    cr.releasePersistableUriPermission(mInputUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
//                    Log.d("activityResult","released " + mInputUri);
//                }

//                cr.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            mInputUri=uri;
//                mTextViewInputFilePath.setText(mInputUri.getPath());
            Cursor cursor=cr.query(mInputUri,null,null,null,null,null);
            if(cursor!=null && cursor.moveToFirst()) {
                mTextViewInputFilePath.setText(cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)));
                Log.d("activityResult",mTextViewInputFilePath.getText().toString());
            }
            cursor.close();
        }
        if(requestCode==CODE_TREE && resultCode== Activity.RESULT_OK) {
            Uri docUri= DocumentsContract.buildDocumentUriUsingTree(uri,
                    DocumentsContract.getTreeDocumentId(uri));

            try(Cursor docCursor = cr.query(docUri, new String[]{
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)){
                if(docCursor!=null && docCursor.moveToFirst()) {
                    Log.d("activityResult", docCursor.getString(docCursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)));
                    mOutputDirUri=uri;
                    mTextViewOutputDir.setText(docCursor.getString(docCursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)));
                }
            }
        }
        if(requestCode==CODE_WRITE && resultCode==Activity.RESULT_OK) {
            mOutputUri=uri;
            try(Cursor cursor=cr.query(mOutputUri,null,null,null,null,null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    mTextViewOutputFilename.setText(cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)));
                    Log.d("activityResult", mTextViewOutputFilename.getText().toString());
                }
            }
        }
    }

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

    public class MyClientTask extends AsyncTask<Void, Void, Void> {
        String mAddr;
        int mPort;
        String mResponse;
        MyClientTask(String addr, int port) {
            mAddr=addr;
            mPort=port;
        }

        @Override
        protected Void doInBackground(Void... params) {
            CharArrayWriter chars=new CharArrayWriter();
            try (Socket socket = new Socket(mAddr,mPort)){
                Log.d("myClientTask","after Socket");
                String data = "aaaa";

                OutputStream os = socket.getOutputStream();
//                try(BufferedOutputStream bos = new BufferedOutputStream(os)) {
//                    bos.write(data.getBytes());
//                    bos.flush();
//                }
//                os.write(data.getBytes());


//                WritableByteChannel channel = Channels.newChannel(os);
                ByteBuffer byteBuffer = ByteBuffer.allocate(8+data.length());
                byteBuffer.put((byte)0);
                byteBuffer.put((byte)1);
                byteBuffer.putShort((short)-1);
                byteBuffer.putInt(data.length());
                byteBuffer.put(data.getBytes());
                os.write(byteBuffer.array());
//                channel.write(byteBuffer);
                StringBuilder stringBuilder = new StringBuilder();
                byte[] buffer = new byte[30];
                int bytesRead;
                InputStream inputStream = socket.getInputStream();
                while ((bytesRead = inputStream.read(buffer))!=-1) {
                    for(Byte by : buffer) {
//                        Log.d("received", String.valueOf(by));
//                        stringBuilder.append(by.toString());
                        chars.write((char)by.byteValue());
                    }
                }
                Log.d("received","bytes Read : " + bytesRead);
//                Log.d("received",String.valueOf(buffer));
//                Log.d("received",stringBuilder.substring(9).toString());
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch(SocketException e){
                e.printStackTrace();
            } catch(IOException e) {
                e.printStackTrace();
            }
            finally {
                if(chars.size()>0) {
                    Log.d("received",chars.toString().substring(8));
                }
                Log.d("myClientTask","end");
            }
            return null;
        }
    }
}
