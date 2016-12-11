var bodyParser = require('body-parser');

var net = require('net');
var server = net.createServer((client) => {
  // console.log("connected with " + client.localAddress);
  // client.setTimeout(500);
  client.bufferSize=30;
  client.on('data', (data)=>{
    // console.log("received data from client");
    // console.log(data.toString());
    // console.log(data.toString('hex'))
    console.log('Bytes received : ' + client.bytesRead);
    data.set(10,68);

    sendingLength = 1024*1024;
    tempRand=((Math.round(Math.random()*100))%2);
    tempRand=1;
    if(tempRand==0) {
      console.log("Ignoring");
      setTimeout(()=>{
      // console.log('end')
      client.end()
    },500);
      return;
    }

    tempRand=((Math.round(Math.random()*100))%2);
    tempRand=1;
    if(tempRand==0)
      sendingLength=5;
    if(client.bytesRead > sendingLength) {
      t = Buffer.alloc(sendingLength);
      data.copy(t,0,0,sendingLength);
      writeData(client,t);
    }
    else {
      writeData(client,data);
    }
  });
  client.on("close", ()=> {
    console.log("clinet closed");
  });
  client.on("error", (err) => {
    console.log("client error");
  });
});
function writeData(socket, data) {
  var success = !socket.write(data,()=>{
    socket.end();
    // setTimeout(()=>{
    //   // console.log('end')
    //   socket.end()
    // },1000);
  });
  if(!success) {
    // ((socket, data)=>{
    //   socket.once("drain",()=>{
    //     writeData(socket,data);
    //   })
    // })(socket,data);
  }
}
server.listen(2012,()=>{
  server.on('close', ()=>{
    console.log("server terminated");
  })
  server.on('error', (err) => {
    console.log("error");
  })
  console.log("waiting on 2012");
});

// var express = require('express');
// var app = express();
// app.use(bodyParser.urlencoded({extended:true}));
// app.use(bodyParser.json({extended:true}));

// app.get('/',(req, res)=>{
//   res.download(__dirname + "/input.txt");
// });
// app.get('/echo',(req, res)=>{
//   console.log(req.query.data);
//   res.send(req.query.data);
// })

// app.listen(2012, () => {
//   console.log("listening on port 2012");
// })