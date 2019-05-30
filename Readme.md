# SocketStudy

慕课网，Socket网络编程进阶与实战<br/><br/>



**代码写于：2019-02 至 2019-05-30**<br/>
  &emsp;JDK：JDK1.8版本，<br/>
  &emsp;IDE：IDEA 2019.1.2，<br/>
  &emsp;操作系统为：Windows10<br/><br/>



**概要**：

主要使用NIO多路复用机制，构建了一个多模块简易聊天室，该聊天室有以下特点

1. 字符串传输
2. 文件传输
3. 语音传输
4. 并发访问，服务端可同时进行十万级连接消息转发或接收
5. 通过消息头机制解决粘包问题
6. 通过消息分片机制实现数据混传，提升大文件传输稳定性
7. 服务端客户端有相应心跳检测
8. 具有三层缓冲区Packet（包级）-> Frame(帧级) -> IOArgs(数据字节级)
9. 手动实现任务窃取线程池优化消息收发机制

 参考资料：<br/>
&emsp;<https://coding.imooc.com/class/286.html> <br/><br/>



**详细介绍**：

TCPDemo：模拟TCP（一服务端对多客户端）  

- 坑点1：BufferedReader的readLine方法读，一直阻塞，后来发现是服务端没向客户端回复，客户端一直阻塞，没有进行第二次发送  
- 坑点2：若流不关闭的话，使用read方法不会读到文件结尾，所以不能用readLine()==null判断是否读完  <br/><br/>



UDPDemo：  

- 简介：模拟UDP通信（发送者向广播地址的20000端口发送信息，告诉所有接收到该消息的回复到30000端口，所有监听20000端口的接收者，接收到信息以后回复到发送者的30000端口）  
- 注意：在UDP通信中，不存在服务端与客户端的说法，因为双方均可进行读写，所以这里的发送者和接收者都是相对的  
- 主要包含：多线程使用和UDP API的使用  
- 曾遇见问题：  
  - I、服务端（UDPListener）处理请求是串行处理的  
    -  解决方案：将UDP的send操作封装为一个Runnable，使用线程池启动  
  - II、UDPReceiver在debug模式下能关闭，在run模式下不能关闭  
    - 忘记关线程池了 <br/> 

      

TCPAndUDPDemo:   

- 模拟场景：客户端需要向服务端发送消息，但不知服务端的具体IP，只知道发送特定消息后，真正的服务端会进行回复， 所以进行UDP广播获取服务端信息，在通过tcp建立连接，相互发送消息(并且因为要相互收发，所以收发线程都是子线程执行非阻塞的)  
- 采用手段：  
  - 服务端：监听协议端口，若收到指定消息头的信息，则按规定进行回复  
  - 客户端：UDP广播获取回应的服务器，从而得知服务端IP，进行TCP连接传输数据  
- 具体协议要求：  
  - UDP ： 客户端会向服务端30201端口发送消息，消息格式为前8byte为7的header,2byte为short，short为1时有效，4byte为int表回送端口号服务端解析成功后回送消息，消息格式为header+命令（2）+端口(客户端应该连接的服务器TCP端口)+消息  

  - TCP : 在TCPDemo中，我们实现的是客户端发送信息服务端阻塞接收并回复，在这里我们需要实现，服务端接收数据的同时能并行的广播数据给所有已连接用户 <br/> 

    

ChatRoom : 使用多模块方法，基于TCPAndUDPDemo的基础上，构建一个多模块简易聊天室(异步阻塞)  

- 性能分析：  
  - 一个客户端 ：双通（读、写线程异步）-> 2个线程  
  - n个客户端 ： 2n个线程  
  - 服务器线程数 ： 大于2n（除了双通线程，还有其他GC、监听、转发消息等线程）  

- 结论：绝大多数线程都是处于等待状态，但由于要发/收n个客户端的消息，所以需要频繁切换线程，造成不必要的资源消耗  <br/>

  

NIOChatRoom ： 使用NIO的多路复用机制，解决ChatRoom绝大多数线程处于等待的问题  

- 优点1（TCPServer）：TCPServer监听等待由原来的子线程阻塞等待改为NIO Selector  

- 优点2（ClientHandler）： 将读写线程改为复用模式

- 优点3 ：读写线程池分离，增大带宽利用，因为网卡也是读写分离的，所以在读的同时也可以写

- 优点4：封装注册中心，读取，心跳包等操作到clink模块，模块分工明确，只需关注本层业务，让上层只关注数据的处理，不必关注SocketChannel和Buffer的衔接

- 优点5：通过固定数据报头部声明数据长度，从而解决粘包和数据不完整问题<br/>

  

**第九章**：为了支持文件传输以及数据混传，还需做以下改变

- 将输入输出使用流的方式进行传输，因为文件比普通字符串大得多

- 先将数据处理好，能够发送，再进行注册监听，可以发送时直接发送  --->修改为懒加载，先进行注册监听，直到能够发送，再进行数据处理。

  - 这样能够保证CPU此时处理的数据一定是可以发送的数据，降低延迟

- 文件传输规则： 
  - ①我们假定文件传输命令为 --f filePath（传输filePath所指向的文件），于是我们在Client的write方法中进行判断。
  - ②文件保存在项目路径/cache/XXX(server或者client)/XXX(业务层自定义)，以.tmp文件的形式保存

- 需进行文件分片，否则会导致以下问题：
  - 发送中无法取消文件发送
  - 传输过程中无法发送其他信息（即无法混传，如果此时有高优先级任务，如文字信息，那么也会被阻塞）
  - 大文件传输容错率低（若分片，能保证出错以前的文件都是正确的）
  - 同一链接无法实现文件、普通消息优先级（即在同一链接中，无法同时发送多个packet，如果有某个优先级低的大文件占用发送链接就会影响体验）

- Dispatcher调度逻辑调整，之前调度级别为packet级别，实现分片后，调度级别为每个片
  - 一开始是packet->IoArgs的**两层**数据缓冲
  - 更改后为packet->Frame->IoArgs**三层**数据缓冲

- 出现的问题及解决方法：
  - 问题：在运行时buffer在setLimit时抛出IllegalArgumentException，点击Buffer源码查看，可能情况是新的limit小于0或者大于capacity，然后观察修改limit值的代码，发现已对上限进行限制，所以初步认为可能是值过大，导致溢出为负数

  - 解决思路：因为该程序缓冲区分三层，packet->Frame->IoArgs，逐层查看limit进行赋值的地方，并在该出进行输出打印，最终发现，是在Frame处出现了负数。**原因**是：byte强转为高字节类型时，缺失位会自动补齐为符号位。于是导致65535的情况使用(byte0<<8)|byte1的方式结果为-1，**修改策略**：直接先与上0xFF将高位置零，然后再进行或操作<br/>

    

**第十章**：①传输框架并发bug修复②三层缓冲区优化③运行状态格式化输出④聊天室消息调度优化⑤心跳包发送与消费

- 自己遇到的一些bug
  - 客户端发送字符串，服务端未接收到：定位后发现只发送了头帧没有发送实体帧（定位方法：接收数据方法一直上移sout排查，直到排查到帧发送处），发现是逻辑错误，在ReveiveComplate回调时未将正在发送状态设为false，导致第二次发送时，仍为正在发送状态。

- 传输框架并发bug修复
  - 当进行多线程传输时发现server端线程均处于monitor状态
    - 解决方案：点击dump，查看monitor线程当前阻塞在哪个地方
  - selector的wakeUp方法返回值不一定为0，所以非0时也要判断锁获取的情况
  - 很多地方竞争资源被修改时未加上同步锁
  - 使用jvisualvm监控当前进程，发现内存占用不断上升
    - 原因：控制台信息输出（sout）的字符串不容易被垃圾回收

- IoArgs优化
  - 当IoArgs读/写（即channel.write/read）为0时，说明当前网卡资源已让出，不再继续读/写入，等待下次读/写事件就绪再写，否则空循环浪费资源

- 消息调度逻辑优化（添加群功能）
  - 一开始统计连接数、处理新消息冗余在一个方法中

    新需求：**需加入群功能**（如加入群，退出群等），加入群以后，每次用户发送消息，会将该消息发送给他最早加入的群

    - 解决方案：将其优化为责任链模式(统计连接数->处理群操作->发送群消息)：先交给链头，链头只能处理统计连接数，不能消费数据，于是再往下抛，下一个链点只能处理群操作，处理结束继续往下抛，如果该链为群类型链，就将其群发处理，不再继续往下抛。遍历完整个责任链，若没有节点能够处理，则进行数据二次消费：回送用户，无法发送消息。

- 构建心跳包

  - 心跳包策略：

    - 最近发送/接收数据到现在超出规定时间，则客户端发送心跳包，服务器接收后不需要回复；因为客户端会收到数据确认包（ACK）。服务端心跳机制间隔时间一般比客户端长，这样当客户端没发送心跳检测服务端才可能会发送检测，则大概率已发生异常

      当客户端尝试发送心跳数据时，可以检测当前是否有数据发送，如果有数据发送则本次心跳包无需发送，因为数据发送本身就是一种心跳检测机制

    - 服务器指定时长(一般为客户端平均发送心跳包间隔时间的5-10倍)扫描客户端进行客户端活跃性扫描，超出时间未活跃则自动关闭链接<br/>



**第十一章**即时语音聊天

- 使用“桥接”技术，具体流程：当有客户端想要加入某个桥接管道时，服务端让其加入，双方所有传输数据不再经过服务端解析（因为服务端解析比较耗时），而是直接转发
  - 语音数据的packet大小是不固定的，不能在构造packet时确定大小，那么只有两种策略：
    - ①等待数据达到指定数据大小时进行打包发送，**缺点**：有延迟，实时性差。
    - ②上一个packet发送结束后查看有多少数据，就发送多少。
    - 这里我们使用的是方案②，因为之前都是定长发送策略，所以要进行一些重构
  - 为什么文件和字符串不使用直接转发，而要经过解析：
    - 文件和字符串：更注重的是完整性和安全性，所以在帧操作时，需要进行封装后多次缓存和校验，因为封装成Packet后就可以进行一些校验码的解析，以确保传输数据是否有丢失。
    - 语音传输：更注重实时性，只进行一次缓存（因为发送和接收速度不同，所以需要一个缓冲区）
  - 难点：如何识别桥接命令（因为当桥接模式启动后，所有数据传输都只通过TCP层直接传输，不再通过业务层解析命令）
    - 再使用一个链接，专门用来传输命令，在传输链接建立前，需要将传输链接与相应的命令链接进行绑定。即一个加入房间者会有3个链接，与服务器的收发链接和命令链接<br/>



**第十二章**性能调优

- 优化请求发送流程
  - 优化前，若某个线程想要发送数据需经过以下步骤：获取注册器锁->唤醒注册器（因为注册器此时在轮询监听事件）->注册输出事件->释放注册器锁->等待注册器轮询到该线程->获取注册器锁->取消事件监听->处理事件 ->判断是否还有数据没发送完，如果还有返回第一步，没有则回调结束
  - 优化后：第一次发送数据时直接处理事件，后续流程与简化前相同
  - 优化思想：第一次申请发送数据时，一般已经就绪才会申请发送，且绝大多数事件都是小数据，发送一次即可完成。不需抢占注册器锁注册事件。这样可优化注册器锁互斥等待时间
    - 多路复用注册器是为了解决：大文件传输时，多次数据发送期间，需要等待数据写入导致CPU空闲。但如果只有一次发送，且发送前已将数据读取，那么多路复用反而会浪费时间
  - 此处可这样优化，是因为在每次数据发送结束，都会判断一下该包是否还有数据未发送，如果有则继续注册，没有则回调结束
- 优化线程数
  - 由一开始的4线程监听发-4线程监听收用于处理文件语音信息，4线程监听收发处理文字信息
  - 优化为1线程监听收发文字语音信息（使用任务窃取），4线程监听收发处理文字信息（不变）
- 任务窃取具体实现
  - 每个参与任务窃取的线程，有一个属性记录该线程完成的任务数，低于一定值时则可认为该线程不繁忙，可去窃取繁忙线程的任务
- 防止生产者消费者速度不同步，造成内存占用过多
  - 生产者使用BlockingQueue设置生产队列大小，当容量满时，生产者会被阻塞
- 将读写线程分离
  - 防止当写过多时，造成所有线程阻塞，读线程也没法申请线程（因为都处于阻塞状态），导致缓冲区被写满却没有线程可以读，使程序完全崩溃
