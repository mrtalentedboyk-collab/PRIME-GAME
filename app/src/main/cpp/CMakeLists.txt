cmake_minimum_required(VERSION 3.10.2)
project("primegame")

add_library(primegame SHARED carrom_engine.cpp)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(primegame ${log-lib} ${jnigraphics-lib})
