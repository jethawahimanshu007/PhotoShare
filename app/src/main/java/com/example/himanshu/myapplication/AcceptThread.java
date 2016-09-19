package com.example.himanshu.myapplication;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import java.io.InputStream;
import java.util.UUID;

/**
 * Created by Himanshu on 8/27/2016.
 */

    class AcceptThread extends Thread {
    Context context;
        private static final UUID MY_UUID =
                UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket,
            // because mmServerSocket is final
            BluetoothServerSocket tmp = null;
            BluetoothAdapter mBluetoothAdapter=BluetoothAdapter.getDefaultAdapter();
            //UUID MY_UUID= UUID.randomUUID();
            Log.d("UUID","UUID is:"+MY_UUID);
            try {
                // MY_UUID is the app's UUID string, also used by the client code
                tmp = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("BTcheck", MY_UUID);
            } catch (Exception e) { }
            mmServerSocket = tmp;
        }

    public AcceptThread(Context context) {
        // Use a temporary object that is later assigned to mmServerSocket,
        // because mmServerSocket is final
        BluetoothServerSocket tmp = null;
        BluetoothAdapter mBluetoothAdapter=BluetoothAdapter.getDefaultAdapter();
        this.context=context;
        //UUID MY_UUID= UUID.randomUUID();
        Log.d("UUID","UUID is:"+MY_UUID);
        try {
            // MY_UUID is the app's UUID string, also used by the client code
            tmp = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("BTcheck", MY_UUID);
        } catch (Exception e) { }
        mmServerSocket = tmp;
    }

    public void run() {
            BluetoothSocket socket = null;
            Log.d("AcceptClass-MainAc","Started listening");
            // Keep listening until exception occurs or a socket is returned
            while (true) {
                try {
                    Log.d("AcceptThread","Going to blocking mode with call to accept");
                    socket = mmServerSocket.accept();
                    Log.d("AcceptThread","Connection is accepted maybe!!!!! The value of connected socket returned is:"+socket);
                    try {
                        ConnectedThreadWithRequestCodes newConnectedThread=new ConnectedThreadWithRequestCodes(socket,context);
                        newConnectedThread.start();
                    }
                    catch(Exception e)
                    {
                        Log.d("AcceptThread","Exception is thrown!! LOL::"+e);
                    }
                    AcceptThread at=new AcceptThread();
                    at.start();
                    ///Directly trying to read instead of using ConnectedThread




                    //test test1=Serializer.bytesToObject(buffer);
                  // Log.d("AcceptThread","Value of test object received, a and b are: "+test1.a+"  "+test1.b);
                    /*ConnectedThreadWithRequestCodes newConnectedThread=new ConnectedThreadWithRequestCodes(socket);
                    newConnectedThread.start();
                    */


                   /* ConnectedThread ct=new ConnectedThread(socket);
                    ct.start();*/

                    //Thread.sleep(500);
                } catch (Exception e) {
                    Log.d("AcceptThread","Exception is thrown, lol..:"+e);
                    break;
                }
                // If a connection was accepted
                if (socket != null) {
                    // Do work to manage the connection (in a separate thread)
                    //manageConnectedSocket(socket);
                    //mmServerSocket.close();
                    break;
                }
            }
        }

        /** Will cancel the listening socket, and cause the thread to finish */
        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (Exception e) { }
        }
    }
