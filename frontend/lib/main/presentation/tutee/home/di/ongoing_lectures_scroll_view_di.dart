import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:tutor_platform/main/domain/repository/lecture_api_repository.dart';
import 'package:tutor_platform/main/domain/use_case/dibs/dibs_lecture.dart';
import 'package:tutor_platform/main/domain/use_case/handle_ongoing_lecture.dart';
import 'package:tutor_platform/main/domain/use_case/list_tile/card_view/get_ongoing_lecture.dart';
import 'package:tutor_platform/main/domain/use_case/obtain_lecture.dart';
import 'package:tutor_platform/main/presentation/components/appbar/common_app_bar.dart';
import 'package:tutor_platform/main/presentation/components/bottom_navigation_bar/tutee/tutee_navigation_bar.dart';
import 'package:tutor_platform/main/presentation/components/lecture_list/infinite_scroll/infinite_scroll_view.dart';
import 'package:tutor_platform/main/presentation/components/lecture_list/scroll_view_model.dart';
import 'package:tutor_platform/main/presentation/components/page_controller_provider.dart';

class OngoingLecturesScrollViewDi extends StatelessWidget {
  const OngoingLecturesScrollViewDi({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: const CommonAppBar(title: '진행 중인 강의', actions: []),
      body: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            MultiProvider(
              providers: [
                ProxyProvider2<HandleOngoingLecture, ObtainLecture, GetOngoingLecture>(
                    update: (context, handLec, getLec, previous) =>
                        GetOngoingLecture(handLec, getLec)),
                ChangeNotifierProvider<ScrollViewModel>(
                  create: (context) => ScrollViewModel(
                    context.read<GetOngoingLecture>(),
                    context.read<DibsLectureUseCase>(),
                  ),
                ),
              ],
              child: const InfiniteScrollView(),
            ),
          ],
        ),
      ),
      bottomNavigationBar: TuteeNavigationBar(
          controller: context.read<PageControllerProvider>().controller
      ),
    );
  }
}
