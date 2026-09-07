package com.example.flashromch341a;

import android.app.Activity;
import android.content.Intent;
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
    private TextView tvProgress;
    private TextView tvBackup;
    private android.widget.ProgressBar progressRead;

    private Button btnDetect;
    private Button btnRead;
    private Button btnWrite;


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
        tvProgress = findViewById(R.id.tvProgress);
        tvBackup = findViewById(R.id.tvBackup);
        progressRead = findViewById(R.id.progressRead);

        btnDetect = findViewById(R.id.btnDetect);
        btnRead = findViewById(R.id.btnRead);

        btnWrite = findViewById(R.id.btnWrite);

        btnWrite.setOnClickListener(v -> {

            openBinPicker();

        });
        btnWrite = findViewById(R.id.btnWrite);



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
                    btnDetect.setEnabled(false);

                    progressRead.setVisibility(android.view.View.VISIBLE);

                    progressRead.setProgress(0);

                    new Thread(() -> {

                        for(int i=0;i<=95;i++){

                            int nilai=i;

                            mainHandler.post(() ->
                                progressRead.setProgress(nilai)
                            );

                            try{
                                Thread.sleep(100);
                            }catch(Exception e){}

                        }

                    }).start();

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
                    btnDetect.setEnabled(true);

                    progressRead.setProgress(100);


                    


                    try{
                        Thread.sleep(500);
                    }catch(Exception e){}

                    progressRead.setVisibility(android.view.View.GONE);

                });


            })

        );


    }





    private String selectedBinPath = "";

    private void openBinPicker(){

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        startActivityForResult(
            Intent.createChooser(intent,"Pilih BIN"),
            200
        );
    }




    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){

        super.onActivityResult(requestCode,resultCode,data);

        if(requestCode==200 && resultCode==RESULT_OK && data!=null){

            selectedBinPath = data.getData().getPath();

            appendLog(
            "BIN DIPILIH:\n"
            + selectedBinPath
            );

            new android.app.AlertDialog.Builder(this)
            .setTitle("Konfirmasi WRITE")
            .setMessage(
            "Tulis IC dengan file:\n\n"
            + selectedBinPath
            + "\n\nPastikan IC benar!"
            )
            .setNegativeButton("BATAL", null)
            .setPositiveButton("WRITE", (d,w) -> {

                new android.app.AlertDialog.Builder(this)
                .setTitle("Backup sebelum WRITE?")
                .setMessage(
                "Backup IC lama sebelum tulis firmware baru?"
                )
                .setNegativeButton("LANJUT WRITE", (x,y) -> {

                    startWriteProcess();

                })
                .setPositiveButton("BACKUP DULU", (x,y) -> {

                    startBackupThenWrite();

                })
                .show();

            })
            .show();

        }

    }



    private void setButtonsEnabled(boolean enable){

        btnDetect.setEnabled(enable);
        btnRead.setEnabled(enable);
        btnWrite.setEnabled(enable);

    }




    private void startWriteProcess(){

        setButtonsEnabled(false);

        progressRead.setVisibility(android.view.View.VISIBLE);
        progressRead.setProgress(0);

        tvProgress.setText(
        "WRITING FLASH..."
        );

        appendLog(
        "--- WRITE FLASH START ---"
        );

        new Thread(() -> {

            if(prepareNativeFiles()){

                runFlashrom(
                "-p ch341a_spi -w "
                + selectedBinPath
                );

            }

            runOnUiThread(() -> {

                progressRead.setProgress(100);

                tvProgress.setText(
                "WRITE SELESAI"
                );

                setButtonsEnabled(true);

            });

        }).start();

    }


    private void startBackupThenWrite(){

        setButtonsEnabled(false);

        progressRead.setVisibility(android.view.View.VISIBLE);
        progressRead.setProgress(0);

        tvProgress.setText(
        "BACKUP IC LAMA..."
        );

        appendLog(
        "--- AUTO BACKUP BEFORE WRITE ---"
        );


        new Thread(() -> {

            runReadFlash();


            try{
                Thread.sleep(3000);
            }catch(Exception e){}


            runOnUiThread(() -> {

                tvProgress.setText(
                "BACKUP SELESAI\nSTART WRITE..."
                );


                startWriteProcess();

            });


        }).start();

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

          mainHandler.post(() -> {

              tvBackup.setText(
              "LAST BACKUP\n"
              + backup.getName()
              );

          });



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

        if(line.contains("Found") && line.contains("flash chip")){

            String chip="-";
            String size="-";

            try{

                int a=line.indexOf("\"");
                int b=line.indexOf("\"",a+1);

                if(a>=0 && b>a){
                    chip=line.substring(a+1,b);
                }

                int c=line.indexOf("(");
                int d=line.indexOf("kB");

                if(c>=0 && d>c){
                    size=line.substring(c+1,d)
                    .trim()
                    +" KB";
                }

            }catch(Exception ignored){}


            tvStatus.setText(
                "STATUS\n"+
                "CH341A : CONNECTED\n"+
                "CHIP : "+chip+"\n"+
                "SIZE : "+size
            );
        }


        if(line.contains("Reading flash")){

            progressRead.setProgress(25);

            tvProgress.setText(
                "READING FLASH...\n25%"
            );
        }


        if(line.contains("Writing flash")){

            progressRead.setProgress(50);

            tvProgress.setText(
                "WRITING FLASH...\n50%"
            );
        }


        if(line.contains("Verifying flash")){

            progressRead.setProgress(80);

            tvProgress.setText(
                "VERIFYING...\n80%"
            );
        }


        if(line.contains("Erase/write done")
        || line.contains("finished")){

            progressRead.setProgress(100);

            tvProgress.setText(
                "FLASH SELESAI\n100%"
            );
        }


        if(line.contains("FAILED")
        || line.contains("ERROR")){

            tvProgress.setText(
                "FLASH ERROR"
            );
        }


        if(line.contains("Couldn't open device")){

            tvStatus.setText(
                "STATUS\n"+
                "CH341A : NOT CONNECTED\n"+
                "CHIP : -\n"+
                "SIZE : -"
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
