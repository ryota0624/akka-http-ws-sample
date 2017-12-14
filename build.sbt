name := "akka-http-websocket"

version := "0.1"

scalaVersion := "2.12.4"

val akkaV = "10.0.11"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http-core" % akkaV, // 主に低レベルのサーバーサイドおよびクライアントサイド HTTP/WebSocket API
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaV, // Akka で JSON を扱う場合はこれ (experimental)
  "com.typesafe.akka" %% "akka-http" % akkaV
)