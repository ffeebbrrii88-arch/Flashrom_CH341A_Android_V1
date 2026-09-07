package com.example.flashromch341a;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executors;


public class MainActivity extends Activity {


    private TextView tvLog;
    private TextView tvStatus;

    private Button btnDetect;
    private Button btnRead;


    private StringBuilder logBuffer = new StringBuilder();


    private String lastChip = "-";
    private String lastSize = "-";
    private String lastBackupFile = "-";


    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());



    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);


        tvLog = findViewById(R.id.tvLog);
        tvStatus = findViewById(R.id.tvStatus);

        btnDetect = findViewById(R.id.btnDetect);
        btnRead = findViewById(R.id.btnRead);



        tvStatus.setText(
                "STATUS\n"+
                "CH341A : NOT CONNECTED\n"+
                "CHIP : -\n"+
                "SIZE : -"
        );



        btnDetect.setOnClickListener(v ->

            Executors.newSingleThreadExecutor().execute(() -> {

                clearLog();

                appendLog(
                "--- DETECT CH341A + IC ---"
                );


                if(prepareNativeFiles()){

                    runFlashrom(
                    "-p ch341a_spi"
                    );

                }

            })

        );



        btnRead.setOnClickListener(v ->

            Executors.newSingleThreadExecutor().execute(() -> {


                clearLog();


                mainHandler.post(() -> {

                    btnRead.setText(
                    "READING..."
                    );

                    btnRead.setEnabled(false);

                });



                appendLog(
                "--- READ BACKUP FLASH ---"
                );


                if(prepareNativeFiles()){

                    runReadFlash();

                }


                mainHandler.post(() -> {

                    btnRead.setText(
                    "READ BACKUP BIN"
                    );

                    btnRead.setEnabled(true);

                });


            })

        );


    }



    private void clearLog(){

        mainHandler.post(() -> {

            logBuffer.setLength(0);
            tvLog.setText("");

        });

    }



    private boolean prepareNativeFiles(){


        String[] files = {

                "flashrom",
                "libcrypto.so.3",
                "libusb-1.0.so"

        };


        File dir = getFilesDir();



        for(String name : files){


            File out =
            new File(dir,name);



            if(!out.exists()){


                try(

                    InputStream in =
                    getAssets().open(name);


                    OutputStream os =
                    new FileOutputStream(out)

                ){


                    byte[] buffer =
                    new byte[1024];


                    int len;


                    while((len=in.read(buffer))!=-1){

                        os.write(buffer,0,len);

                    }



                    appendLog(
                    "Copy: "+name
                    );



                }catch(Exception e){


                    appendLog(
                    "ERROR "+e.getMessage()
                    );


                    return false;

                }

            }


            out.setExecutable(true,false);


        }


        return true;

    }

    private void runFlashrom(String args){

        executeCommand(
                getFilesDir().getAbsolutePath()
                + "/flashrom "
                + args
        );
    }


    private void runReadFlash(){

        File backup =
                new File(
                "/sdcard/Download",
                "FLASHROM_backup_"
                + new SimpleDateFormat("yyyyMMdd_HHmm")
                .format(new Date())
                + ".bin"
                );


        appendLog(
        "Backup file:\n"
        + backup.getAbsolutePath()
        );


        executeCommand(
        getFilesDir().getAbsolutePath()
        + "/flashrom -p ch341a_spi -r "
        + backup.getAbsolutePath()
        );


    }


    private void executeCommand(String command){

        try{

            Process process =
            Runtime.getRuntime().exec("su");


            OutputStream os =
            process.getOutputStream();


            InputStream input =
            process.getInputStream();


            InputStream error =
            process.getErrorStream();


            os.write(
            ("export LD_LIBRARY_PATH="
            + getFilesDir().getAbsolutePath()
            + "\n"
            + command
            + "\nexit\n").getBytes()
            );


            os.flush();


            new Thread(() -> readStream(input)).start();

            new Thread(() -> readStream(error)).start();


            process.waitFor();


        }catch(Exception e){

            appendLog(
            "[ERROR] "
            + e.getMessage()
            );

        }

    }



    private void readStream(InputStream is){

        try{

            BufferedReader br =
            new BufferedReader(
            new InputStreamReader(is)
            );


            String line;


            while((line=br.readLine())!=null){

                String text=line;


                mainHandler.post(() -> {

                    appendLog(text);
                    updateStatus(text);

                });

            }


        }catch(Exception e){

            appendLog(e.getMessage());

        }

    }




    private void updateStatus(String line){


        if(line.contains("Found")
        && line.contains("flash chip")){


            tvStatus.setText(
            "STATUS\n"
            +"CH341A : CONNECTED\n"
            +"CHIP : DETECTED\n"
            +"SIZE : OK"
            );


        }


        if(line.contains("Couldn't open device")){


            tvStatus.setText(
            "STATUS\n"
            +"CH341A : NOT CONNECTED\n"
            +"CHIP : -\n"
            +"SIZE : -"
            );

        }


    }




    private void appendLog(String text){

        mainHandler.post(() -> {


            logBuffer.append(text)
            .append("\n");


            if(logBuffer.length()>8000){

                logBuffer.delete(
                0,
                logBuffer.length()-8000
                );

            }


            tvLog.setText(
            logBuffer.toString()
            );


        });

    }

}
