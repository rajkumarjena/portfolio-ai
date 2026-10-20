Portfolio Summary Widget.
1. I wanted to create Portfolio Summary Widget for Bonds, FDs, Shares, Mutual fund etc. But now I want Bonds Widget for this development.
2. All the Widget to be on top of the screen from left to right. and current screen should be as is but it should be below top widget panel.
3. The widget will look like below in a table with information as given in sample. where column should be like Bond Return, Self and Family as given in below sample widget report.
4. Return for self means, the return of the logged in user. when return of Family means, sum of return of client and his or her associated family member.
   4.a. Total Bond Investment means, sum of client's current total investment on all bonds. If from the investment amount some amount already credited back to client as redemption then remaining amount should be considered as current investment. you can find redemption amout from Bond Returns Report screen. calculation should be on top of active bonds i.e bond maturity date in future from current date.
   4.b. Total Bond Investment Family means : sum of client's and his associated family member's current total investment on all bonds which are active. this information can be found from Bond Return Reports screen.
   4.c. Similarlly data for 'Return This Week', return of this week means, sum of redempiton amount and interest which are incurred from current date till end of this week as per calendar. Similarlly 'Return This week' family means, sum of redempiton amount plus interest for client and his or family.
   4.d. Calculation should be similar for Month, Quarter and Year as explained in above.
5. Date calculation like for current week, it should show date range from today till 25th October. and for Month it should be curren date till 31st October 2026. Other will be like explained for week or month.
6. Move maximum logic to serverside.
7. Design wedget as best professional with good look and fill. follow any best design for Dashboard where wedget are displayed. we hve another 5 to 6 widget to come up in future

Bond Return 	Self	Family
Total Investment	A1	B1
Return This Week	A2	B2
Return This Month	A3	B3
Return This Quarter	A4	B4
Return This Year	A5	B5