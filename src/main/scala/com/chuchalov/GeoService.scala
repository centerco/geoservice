package com.chuchalov

import com.twitter.finagle._
import com.twitter.finagle.http._
import com.twitter.io.Buf
import com.twitter.util._

import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.util.Random


object GeoService extends App {
  val service: Service[Request, Response] = new Service[Request, Response] {

    case class Geo(lat: Float, lon: Float)
    case class GridCell(tileX: Int, tileY: Int)

    val userKV: mutable.Map[Int, Geo] = TrieMap.empty[Int, Geo]
    val gridKV: mutable.Map[GridCell, Float] = TrieMap.empty[GridCell, Float]

    def generateGrid(): Unit = {
      var tileX = -90
      var tileY = -180

      val dummy = 0.1f

      while (tileX <= 90) {
        while (tileY <= 180) {
          gridKV.update(GridCell(tileX, tileY), dummy)
          tileY = tileY + 2
        }
        tileX = tileX + 2
        tileY = -180
      }

    }

    def calculateDistance(x: Double): Double = Math.PI / 180 * 6378.1 * Math.cos(x)

    def checkNearLabel(id: Int, geo: Geo): Boolean = {
      val data = userKV.getOrElseUpdate(id, geo)
      if (data == geo) {
        return true
      }

      var latGrid = Math.round(data.lat)
      var lonGrid = Math.round(data.lon)

      if (latGrid % 2 != 0) {
        latGrid = latGrid + 1
      }

      if (lonGrid % 2 != 0) {
        lonGrid = lonGrid + 1
      }

      val gridError = gridKV(GridCell(latGrid, lonGrid))

      if (calculateDistance(Math.abs(geo.lon) * Math.abs(geo.lon - data.lon)) < gridError &&
        calculateDistance(Math.abs(data.lat) * Math.abs(geo.lat - data.lat)) < gridError) {
        return true
      }
      
      false
    }

    def apply(req: Request): Future[Response] = {
      val resp = Response(req.version, new http.Status(200))
      val key = req.uri

      req.method match {

        case Method.Get =>
          if (key.startsWith("/health")) {
            resp.setContentString("ok")
          }

          else if (key.startsWith("/check")) {
            val data = key.substring(7).split(",")
            val idx = data(0).toInt
            val geo = Geo(data(1).toFloat, data(2).toFloat)

            if(checkNearLabel(idx, geo)) {
              resp.setContentString("Near")
            }
            else {
              resp.setContentString("Far")
            }
          }

          else {
            userKV.get(key.substring(1).toInt) match {
              case None =>
                resp.status(http.Status(404))
              case Some(value) =>
                resp.setContentString(value.toString)
            }
          }

        case Method.Post =>
          val data = Buf.decodeString(req.content, StandardCharsets.UTF_8).split(",")
          userKV.update(data(0).toInt, Geo(data(1).toFloat, data(2).toFloat))

        case Method.Delete =>
          val data = Buf.decodeString(req.content, StandardCharsets.UTF_8).toInt
          userKV.drop(data)

        case Method.Put =>
          val limit = Buf.decodeString(req.content, StandardCharsets.UTF_8).toInt
          val r: Random.type = scala.util.Random

          for (i <- 1 to limit) {
            val lat = r.nextInt(180) - 90 + r.nextFloat()
            val lon = r.nextInt(360) - 180 + r.nextFloat()

            userKV.update(i, Geo(lat, lon))
          }

          new PrintWriter("table1.txt") {
            for (i <- 1 to userKV.size) {
              write(f"$i,${userKV(i).lat}%.6f,${userKV(i).lon}%.6f\n")
            }
            close()
          }

          generateGrid()
          new PrintWriter("table2.txt") {
            gridKV.foreach(f => {
              write(f"${f._1.tileX}${f._1.tileY},${f._2}\n")
            })
            close()
          }

        case _ =>
          resp.status(http.Status(400))
      }
      Future.apply(resp)
    }
  }

  val server = Http.serve(":8080", service)
  Await.ready(server)

}
